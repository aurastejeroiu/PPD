using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace Lab_4
{
    public static class CallBackExecution
    {
        public static void Run(List<string> hosts)
        {
            for (var i = 0; i < hosts.Count; i++)
            {
                Download(hosts[i], i);
                Thread.Sleep(1000); // this is needed to not overlap the requests
            }
        }

        private static void Download(string host, int id)
        {
            var ipHostInfo = Dns.GetHostEntry(host.Split('/')[0]);
            var ipAddress = ipHostInfo.AddressList[0];
            var remoteEndpoint = new IPEndPoint(ipAddress, Util.Port);

            var client = new Socket(ipAddress.AddressFamily, SocketType.Stream, ProtocolType.Tcp);

            var requestSocket = new Content
            {
                Socket = client,
                HostName = host.Split('/')[0],
                Endpoint = host.Contains("/") ? host[host.IndexOf("/", StringComparison.Ordinal)..] : "/",
                RemoteEndPoint = remoteEndpoint,
                Id = id
            };

            requestSocket.Socket.BeginConnect(requestSocket.RemoteEndPoint, AfterConnect,
                requestSocket); // connect to the remote endpoint
        }

        private static void AfterConnect(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            content.Socket.EndConnect(ar);
            Console.WriteLine("Connection {0} > Socket connected to {1} ({2})", content.Id, content.HostName,
                content.RemoteEndPoint);

            var byteData = Encoding.ASCII.GetBytes(Util.GetRequestString(content.HostName, content.Endpoint));

            content.Socket.BeginSend(byteData, 0, byteData.Length, 0, AfterSend, content);
        }

        private static void AfterSend(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            // send data to server
            Console.WriteLine("Connection {0} > Sent {1} bytes to server.", content.Id, content.Socket.EndSend(ar));

            // server response (data)
            content.Socket.BeginReceive(content.Buffer, 0, Content.BufferSize, 0, AfterReceive, content);
        }

        private static void AfterReceive(IAsyncResult ar)
        {
            var content = (Content) ar.AsyncState;
            if (content == null) return;

            var bytesRead = content.Socket.EndReceive(ar); // read response data

            content.ResponseContent.Append(Encoding.ASCII.GetString(content.Buffer, 0, bytesRead));

            // if the response header has not been fully obtained, get the next chunk of data
            if (!Util.ResponseHeaderObtained(content.ResponseContent.ToString()))
            {
                content.Socket.BeginReceive(content.Buffer, 0, Content.BufferSize, 0, AfterReceive,
                    content.Socket);
            }
            else
            {
                Console.WriteLine("Content length is:{0}",
                    Util.GetContentLength(content.ResponseContent.ToString()));
                Console.WriteLine("Content {0}", content.ResponseContent.ToString());

                content.Socket.Shutdown(SocketShutdown.Both); // free socket
                content.Socket.Close();
            }
        }
    }
}