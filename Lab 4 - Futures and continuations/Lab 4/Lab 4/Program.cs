using System;
using System.Linq;

namespace Lab_4
{
    internal static class Program
    {
        private static void Main()
        {
            var hosts = new[]
            {
                "www.cs.ubbcluj.ro/~rlupsa/edu/pdp/",
                "www.cs.ubbcluj.ro/~forest",
                "www.cs.ubbcluj.ro/~motogna/LFTC"
            }.ToList();

            // Console.WriteLine("Hello World!");
            CallBackExecution.Run(hosts);
            // TaskExecution.Run(hosts);
            AsyncTaskExecution.Run(hosts);
        }
    }
}