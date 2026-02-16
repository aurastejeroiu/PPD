using System;
using System.IO;
using Laboratory4.Models;

namespace Laboratory4.Utils
{
    internal static class ResponseBuilder
    {
        public static void BuildResponse(Message message)
        {
            Console.WriteLine(nameof(BuildResponse));
            // Write the string array to a new file named "WriteLines.txt".
            using var outputFile = new StreamWriter(Path.Combine(ProgramConstants.ResponsePath, $"{message.Hostname}_{message.Id}.txt"));
            foreach (var line in message.ResponseContent.ToString().Split('\r', '\n'))
                outputFile.WriteLine(line);
        }
    }
}