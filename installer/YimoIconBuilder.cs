using System;
using System.Drawing;
using System.Drawing.Drawing2D;
using System.Drawing.Imaging;
using System.IO;

internal static class YimoIconBuilder
{
    private static int Main(string[] args)
    {
        if (args.Length != 2)
        {
            return 2;
        }

        int[] sizes = new int[] { 16, 32, 48, 256 };
        byte[][] images = new byte[sizes.Length][];
        using (Bitmap source = new Bitmap(args[0]))
        {
            for (int index = 0; index < sizes.Length; index++)
            {
                using (Bitmap icon = DrawIcon(source, sizes[index]))
                {
                    images[index] = EncodeDib(icon);
                }
            }
        }
        WriteIco(args[1], sizes, images);
        return 0;
    }

    private static Bitmap DrawIcon(Bitmap source, int size)
    {
        Bitmap bitmap = new Bitmap(size, size, PixelFormat.Format32bppArgb);
        using (Graphics graphics = Graphics.FromImage(bitmap))
        {
            graphics.SmoothingMode = SmoothingMode.HighQuality;
            graphics.InterpolationMode = InterpolationMode.HighQualityBicubic;
            graphics.PixelOffsetMode = PixelOffsetMode.HighQuality;
            graphics.Clear(Color.Transparent);
            graphics.DrawImage(source, new Rectangle(0, 0, size, size),
                    0, 0, source.Width, source.Height, GraphicsUnit.Pixel);
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
