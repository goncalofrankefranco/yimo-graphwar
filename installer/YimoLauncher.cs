using System;
using System.Diagnostics;
using System.IO;
using System.Windows.Forms;

internal static class YimoLauncher
{
    [STAThread]
    private static int Main()
    {
        string baseDirectory = AppDomain.CurrentDomain.BaseDirectory;
        string java = Path.Combine(baseDirectory, "runtime", "bin", "javaw.exe");
        string jar = Path.Combine(baseDirectory, "YIMO-Graphwar-2.0.0.jar");
        string config = Path.Combine(baseDirectory, "yimo.properties");
        try
        {
            ProcessStartInfo start = new ProcessStartInfo(java);
            start.WorkingDirectory = baseDirectory;
            start.UseShellExecute = false;
            start.Arguments = "-Xms64m -Xmx256m -Dfile.encoding=UTF-8 -jar "
                    + Quote(jar) + " --config " + Quote(config);
            Process.Start(start);
            return 0;
        }
        catch (Exception error)
        {
            MessageBox.Show("YIMO Graphwar could not start.\n\n" + error.Message,
                    "YIMO Graphwar", MessageBoxButtons.OK, MessageBoxIcon.Error);
            return 1;
        }
    }

    private static string Quote(string value)
    {
        return "\"" + value.Replace("\"", "\\\"") + "\"";
    }
}
