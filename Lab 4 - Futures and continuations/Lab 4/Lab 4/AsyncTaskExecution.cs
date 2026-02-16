using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;

namespace Lab_4
{
    public static class AsyncTaskExecution
    {
        private static List<string> _hosts;

        public static void Run(List<string> hostnames)
        {
            _hosts = hostnames;
            var tasks = new List<Task>();

            for (var i = 0; i < hostnames.Count; i++)
            {
                tasks.Add(Task.Factory.StartNew(Download, i));
            }

            Task.WaitAll(tasks.ToArray());
        }

        private static void Download(object idObject)
        {
            var id = (int) idObject;
            BeginDownload(_hosts[id], id);
        }

        private static async void BeginDownload(string host, int id)
        {
            var ipHostInfo = Dns.GetHostEntry(host.Split('/')[0]);
            var ipAddress = ipHostInfo.AddressList[0];

            var client = new Socket(ipAddress.AddressFamily, SocketType.Stream, ProtocolType.Tcp);

            var requestSocket = new Content()
            {
                Socket = client,
                HostName = host.Split('/')[0],
                Endpoint = host.Contains("/") ? host[host.IndexOf("/", StringComparison.Ordinal)..] : "/",
                RemoteEndPoint = new IPEndPoint(ipAddress, Util.Port),
                Id = id
            };

            await StartConnect(requestSocket); // connect to remote server
            await StartSend(requestSocket,
                Util.GetRequestString(requestSocket.HostName, requestSocket.Endpoint)); // request data from server
            await StartReceive(requestSocket); // receive server response

            Console.WriteLine("Connection {0} > Content length is:{1}", requestSocket.Id,
                Util.GetContentLength(requestSocket.ResponseContent.ToString()));

            // release the socket
            client.Shutdown(SocketShutdown.Both);
            client.Close();
        }

        private static async Task StartConnect(Content state)
        {
            state.Socket.BeginConnect(state.RemoteEndPoint, AfterConnect, state);

            await Task.FromResult(state.ConnectionDone.WaitOne()); // block until signaled
        }

        private static void AfterConnect(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            content.Socket.EndConnect(ar); // complete connection

            Console.WriteLine("Connection {0} > Socket connected to {1} ({2})", content.Id, content.HostName,
                content.Socket.RemoteEndPoint);

            content.ConnectionDone.Set(); // signal connection is done
        }

        private static async Task StartSend(Content content, string data)
        {
            var byteData = Encoding.ASCII.GetBytes(data);
            content.Socket.BeginSend(byteData, 0, byteData.Length, 0, AfterSend, content);

            await Task.FromResult(content.SendingDone.WaitOne());
        }

        private static void AfterSend(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            var bytesSent = content.Socket.EndSend(ar); // complete sending the data to the server  

            Console.WriteLine("Connection {0} > Sent {1} bytes to server.", content.Id, bytesSent);

            content.SendingDone.Set(); // signal that all bytes have been sent
        }

        private static async Task StartReceive(Content content)
        {
            // receive data
            content.Socket.BeginReceive(content.Buffer, 0, Content.BufferSize, 0, AfterReceive, content);

            await Task.FromResult(content.ReceivingDone.WaitOne());
        }

        private static void AfterReceive(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            // read data from the remote device.  
            var bytesRead = content.Socket.EndReceive(ar);

            content.ResponseContent.Append(Encoding.ASCII.GetString(content.Buffer, 0, bytesRead));

            // if the response header has not been fully obtained, get the next chunk of data
            if (!Util.ResponseHeaderObtained(content.ResponseContent.ToString()))
            {
                content.Socket.BeginReceive(content.Buffer, 0, Content.BufferSize, 0, AfterReceive,
                    content);
            }
            else
            {
                content.ReceivingDone.Set();
            }
        }
    }
}