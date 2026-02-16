using System;
using System.Collections.Generic;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading.Tasks;
using Laboratory4.Models;
using Laboratory4.Utils;

namespace Laboratory4.Service
{
    public static class TaskCallback
    {
        public static void Execute()
        {
            var tasks = new List<Task>();
            ProgramConstants.Hosts
                .ToList().ForEach(e =>
                {
                    Console.WriteLine(nameof(Execute));
                    tasks.Add(Task.Factory.StartNew(Run, e));
                });
            Task.WaitAll(tasks.ToArray());
        }

        private static void Run(object e)
        {
            var id = new Random().Next();
            var host = ((string) e).Split("/")[0];
            var hostEntry = Dns.GetHostEntry(host);
            var endpoint = new IPEndPoint(hostEntry.AddressList[0], ProgramConstants.DefaultPort);
            var socket = new Socket(endpoint.AddressFamily,
                SocketType.Stream,
                ProtocolType.Tcp);
            
            var message = new Message
            {
                Id = id,
                Socket = socket,
                Hostname = host,
                Endpoint = host.Contains("/") ? host[host.IndexOf("/", StringComparison.Ordinal)..] : "/",
                RemoteEndpoint = endpoint
            };
            
            OnConnect(message).Wait();
            OnSend(message, ProgramConstants.BuildHeaderRequest(message.Endpoint, message.Hostname)).Wait();
            OnReceive(message).Wait();
            
            ResponseBuilder.BuildResponse(message);
            socket.Shutdown(SocketShutdown.Both);
            socket.Close();
        }

        private static Task OnReceive(Message message)
        {
            message.Socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, ReceiveCallback, message);
            return Task.FromResult(message.ReceiveDone.WaitOne());
        }

        private static void ReceiveCallback(IAsyncResult ar)
        {
            var message = (Message) ar.AsyncState;
            if (message == null) return;
            var socket = message.Socket; 

            try {
                var bytesRead = socket.EndReceive(ar);
                message.ResponseContent.Append(Encoding.ASCII.GetString(message.Buffer, 0, bytesRead));
                if (!message.ResponseContent.ToString().Contains("\r\n\r\n")) {
                    socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, ReceiveCallback, message);
                } else {
                    var responseBody = ProgramConstants.GetResponseBody(message.ResponseContent.ToString());
                    if (responseBody.Length < ProgramConstants.GetContentLength(message.ResponseContent.ToString())) {
                        socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, ReceiveCallback, message);
                    } else {
                        message.ReceiveDone.Set();
                    }
                }
            } catch (Exception e) {
                Console.WriteLine(e.ToString());
            }
        }

        private static Task OnSend(Message message, string buildHeaderRequest)
        {
            var byteData = Encoding.ASCII.GetBytes(buildHeaderRequest);
            message.Socket.BeginSend(byteData, 0, byteData.Length, 0, SendCallback, message);
            return Task.FromResult(message.SendDone.WaitOne());
        }

        private static void SendCallback(IAsyncResult ar)
        {
            var message = (Message) ar.AsyncState;
            if (message == null) return;
            var clientSocket = message.Socket;
            clientSocket.EndSend(ar);
            message.SendDone.Set();
        }


        private static Task OnConnect(Message message)
        {
            message.Socket.BeginConnect(message.RemoteEndpoint, OnConnectCallback, message);
            return Task.FromResult(message.ConnectDone.WaitOne());
        }

        private static void OnConnectCallback(IAsyncResult ar)
        {
            var message = (Message) ar.AsyncState;
            if (message == null) return;
            var socket = message.Socket;
            socket.EndConnect(ar);
            message.ConnectDone.Set();
        }
    }
}