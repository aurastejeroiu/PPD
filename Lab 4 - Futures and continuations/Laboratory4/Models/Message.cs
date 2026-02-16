using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace Laboratory4.Models
{
    public class Message
    {
        public Socket Socket = null;

        public const int BufferSize = 512;

        public readonly byte[] Buffer = new byte[BufferSize];

        public readonly StringBuilder ResponseContent = new();

        public int Id;

        public string Hostname;

        public string Endpoint;
        
        public IPEndPoint RemoteEndpoint;

        public readonly ManualResetEvent ConnectDone = new(false);

        public readonly ManualResetEvent SendDone = new(false);

        public readonly ManualResetEvent ReceiveDone = new(false);
    }
}