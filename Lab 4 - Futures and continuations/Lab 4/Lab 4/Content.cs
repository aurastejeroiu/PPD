using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace Lab_4
{
    public class Content
    {
        public Socket Socket = null;

        public const int BufferSize = 256;

        public readonly byte[] Buffer = new byte[BufferSize];

        public readonly StringBuilder ResponseContent = new();

        public int Id; // unique id for each custom socket
        public string HostName;
        public string Endpoint;

        public IPEndPoint RemoteEndPoint;

        // these are needed for task implementations
        public readonly ManualResetEvent ConnectionDone = new(false);
        public readonly ManualResetEvent SendingDone = new(false);
        public readonly ManualResetEvent ReceivingDone = new(false);
    }
}