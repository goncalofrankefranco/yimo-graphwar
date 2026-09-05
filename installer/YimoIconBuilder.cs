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

        int[] sizes = new int[] { 16, 32, 48, 256 };
        byte[][] images = new byte[sizes.Length][];
        for (int index = 0; index < sizes.Length; index++)
        {
            using (Bitmap bitmap = DrawIcon(sizes[index]))
            {
                images[index] = EncodeDib(bitmap);
            }
        }
        WriteIco(args[0], sizes, images);
        return 0;
    }

    private static Bitmap DrawIcon(int size)
    {
        Bitmap bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(bitmap))
        {
            graphics.SmoothingMode = SmoothingMode.AntiAlias;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.ScaleTransform((float)size / Size, (float)size / Size);
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
        }
        return bitmap;
    }

    private static byte[] EncodeDib(Bitmap bitmap)
    {
        int size = bitmap.Width;
        int xorStride = size * 4;
        int xorBytes = xorStride * size;
        int andStride = ((size + 31) / 32) * 4;
        int andBytes = andStride * size;
        using (MemoryStream stream = new MemoryStream())
        using (BinaryWriter writer = new BinaryWriter(stream))
        {
            writer.Write((uint)40);
            writer.Write((int)size);
            writer.Write((int)size * 2);
            writer.Write((ushort)1);
            writer.Write((ushort)32);
            writer.Write((uint)0);
            writer.Write((uint)xorBytes);
            writer.Write((int)0);
            writer.Write((int)0);
            writer.Write((uint)0);
            writer.Write((uint)0);
            for (int y = size - 1; y >= 0; y--)
            {
                for (int x = 0; x < size; x++)
                {
                    Color color = bitmap.GetPixel(x, y);
                    writer.Write(color.B);
                    writer.Write(color.G);
                    writer.Write(color.R);
                    writer.Write(color.A);
                }
            }
            for (int index = 0; index < andBytes; index++)
            {
                writer.Write((byte)0);
            }
            return stream.ToArray();
        }
    }

    private static void WriteIco(string path, int[] sizes, byte[][] images)
    {
        using (FileStream file = new FileStream(path, FileMode.Create, FileAccess.Write))
        using (BinaryWriter writer = new BinaryWriter(file))
        {
            writer.Write((ushort)0);
            writer.Write((ushort)1);
            writer.Write((ushort)sizes.Length);
            int offset = 6 + (16 * sizes.Length);
            for (int index = 0; index < sizes.Length; index++)
            {
                byte dimension = sizes[index] >= 256 ? (byte)0 : (byte)sizes[index];
                writer.Write(dimension);
                writer.Write(dimension);
                writer.Write((byte)0);
                writer.Write((byte)0);
                writer.Write((ushort)1);
                writer.Write((ushort)32);
                writer.Write((uint)images[index].Length);
                writer.Write((uint)offset);
                offset += images[index].Length;
            }
            for (int index = 0; index < images.Length; index++)
            {
                writer.Write(images[index]);
            }
        }
    }
}
