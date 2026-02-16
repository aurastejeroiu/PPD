using System;

namespace Lab_4
{
    public static class Util
    {
        public const int Port = 80;

        public static string GetRequestString(string hostname, string endpoint)
        {
            return "GET " + endpoint + " HTTP/1.1\r\n" +
                   "Host: " + hostname + "\r\n" +
                   "Content-Length: 0\r\n\r\n";
        }

        public static int GetContentLength(string respContent)
        {
            var contentLength = 0;
            foreach (var line in respContent.Split('\r', '\n'))
            {
                var headerDetails = line.Split(':');
                if (string.Compare(headerDetails[0], "Content-Length", StringComparison.Ordinal) == 0)
                    contentLength = int.Parse(headerDetails[1]);
            }

            return contentLength;
        }

        public static bool ResponseHeaderObtained(string response)
        {
            return response.Contains("\r\n\r\n");
        }
    }
}