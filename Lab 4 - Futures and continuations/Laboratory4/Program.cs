using System;
using System.Threading;
using Laboratory4.Service;
using Laboratory4.Utils;

namespace Laboratory4
{
    internal static class Program
    {
        private static void Main(string[] args)
        {
            switch (ProgramConstants.ExecutionType)
            {
                case ExecutionType.Serial:
                {
                    SerialCallback.Execute();
                    break;
                }
                case ExecutionType.Tasks:
                {
                    TaskCallback.Execute();
                    break;
                }
                case ExecutionType.AsyncTasks:
                {
                    AsyncTaskCallback.Execute();
                    break;
                }
                default:
                    throw new Exception($"The Callback type in the {nameof(ProgramConstants)} was not found...");
            }
            Thread.Sleep(1000);
        }
    }
}