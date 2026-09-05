using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

internal static class YimoIconBuilder
{
    private const int Size = 256;

    private static int Main(string[] args)
    {
        if (args.Length != 1)
        {
            return 2;
        }

        using (Bitmap bitmap = new Bitmap(Size, Size, PixelFormat.Format32bppArgb))
        using (Graphics graphics = Graphics.FromImage(bitmap))
        {
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.Clear(Color.FromArgb(8, 14, 20));

            using (Pen ring = new Pen(Color.FromArgb(242, 163, 91), 8.0f))
            using (Pen grid = new Pen(Color.FromArgb(248, 246, 239, 35), 1.0f))
            using (Pen trajectory = new Pen(Color.FromArgb(242, 163, 91), 6.0f))
            {
                graphics.DrawEllipse(ring, 26, 26, 204, 204);
                for (int position = 40; position < Size; position += 43)
                {
                    graphics.DrawLine(grid, position, 0, position - 72, Size);
                    graphics.DrawLine(grid, 0, position, Size, position);
                }
                trajectory.StartCap = LineCap.Round;
                trajectory.EndCap = LineCap.Round;
                trajectory.DashStyle = DashStyle.Dash;
                using (GraphicsPath path = new GraphicsPath())
                {
                    path.AddBezier(20, 203, 74, 143, 151, 168, 236, 66);
                    graphics.DrawPath(trajectory, path);
                }
            }

            using (Font font = new Font("Georgia", 116.0f, FontStyle.Bold, GraphicsUnit.Pixel))
            using (SolidBrush white = new SolidBrush(Color.FromArgb(248, 246, 239)))
            using (StringFormat format = new StringFormat())
            {
                format.Alignment = StringAlignment.Center;
                format.LineAlignment = StringAlignment.Center;
                graphics.DrawString("Y", font, white, new RectangleF(0, 12, Size, Size - 4), format);
            }

            byte[] png;
            using (MemoryStream stream = new MemoryStream())
            {
                bitmap.Save(stream, ImageFormat.Png);
                png = stream.ToArray();
            }
            WriteIco(args[0], png);
        }
        return 0;
    }

    private static void WriteIco(string path, byte[] png)
    {
        using (FileStream file = new FileStream(path, FileMode.Create, FileAccess.Write))
        using (BinaryWriter writer = new BinaryWriter(file))
        {
            writer.Write((ushort)0);
            writer.Write((ushort)1);
            writer.Write((ushort)1);
            writer.Write((byte)0);
            writer.Write((byte)0);
            writer.Write((byte)0);
            writer.Write((byte)0);
            writer.Write((ushort)1);
            writer.Write((ushort)32);
            writer.Write((uint)png.Length);
            writer.Write((uint)22);
            writer.Write(png);
        }
    }
}
