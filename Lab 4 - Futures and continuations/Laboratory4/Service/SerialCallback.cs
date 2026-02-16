using System;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Text;
using Laboratory4.Models;
using Laboratory4.Utils;

namespace Laboratory4.Service
{
    public static class SerialCallback 
    {
        public static void Execute()
        {
            ProgramConstants.Hosts.ToList().ForEach(elem =>
                {
                    var id = new Random().Next();
                    var host = elem.Split("/")[0];
                    var hostInformation = Dns.GetHostEntry(host);
                    var addresses = hostInformation.AddressList[0];
                    var remoteEndpoint = new IPEndPoint(addresses, ProgramConstants.DefaultPort);
                    var socket = new Socket(remoteEndpoint.AddressFamily,
                        SocketType.Stream,
                        ProtocolType.Tcp);
                    var state = new Message
                    {
                        Socket = socket,
                        Hostname = host,
                        Endpoint = host.Contains("/") ? host[host.IndexOf("/", StringComparison.Ordinal)..] : "/",
                        RemoteEndpoint = remoteEndpoint,
                        Id = id
                    };
                    socket.BeginConnect(state.RemoteEndpoint, OnConnect, state);
                }
            );
        }

        private static void OnConnect(IAsyncResult ar)
        {
            var message = (Message)ar.AsyncState;
            if (message == null) return;
            message.Socket.EndConnect(ar);
            Console.WriteLine(nameof(OnConnect));
            var header =
                Encoding.ASCII.GetBytes(ProgramConstants.BuildHeaderRequest(message.Endpoint, message.Hostname));
            message.Socket.BeginSend(header, 0, header.Length, 0, OnSend, message);
        }

        private static void OnSend(IAsyncResult ar)
        {
            Console.WriteLine(nameof(OnSend));
            var message = (Message)ar.AsyncState;
            message?.Socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, OnReceive, message);
        }

        private static void OnReceive(IAsyncResult ar)
        {
            var message = (Message)ar.AsyncState;
            if (message == null) return;
            try
            {
                var bytes = message.Socket.EndReceive(ar);
                var socket = message.Socket;
                message.ResponseContent.Append(Encoding.ASCII.GetString(message.Buffer, 0, bytes));
                if (!message.ResponseContent.ToString().Contains("\r\n\r\n"))
                {
                    socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, OnReceive, message);
                }
                else
                {
                    var body = ProgramConstants.GetResponseBody(message.ResponseContent.ToString());
                    var headerLength = ProgramConstants.GetContentLength(message.ResponseContent.ToString());
                    if (body.Length < headerLength)
                    {
                        socket.BeginReceive(message.Buffer, 0, Message.BufferSize, 0, OnReceive, message);
                    }
                    else
                    {
                        ResponseBuilder.BuildResponse(message);
                        socket.Shutdown(SocketShutdown.Both);
                        socket.Close();
                    }
                }
            }
            catch (Exception e)
            {
                throw new Exception(e.Message);
            }
        }
    }
}