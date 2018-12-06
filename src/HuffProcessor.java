import java.util.PriorityQueue;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}

	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void compress(BitInputStream in, BitOutputStream out)
	{
		int counts[] = makeCounts(in);
		HuffNode root = makeTree(counts);
		String[] codes = makeCodes(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeTree(root, out);
		in.reset();
		writeCompressedBits(codes, in, out);
		out.close();
	}
	
	public int[] makeCounts(BitInputStream in)
	{
		int[] counts = new int[ALPH_SIZE + 1];
		while (true)
		{
			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) break;
			counts[value] += 1;
		}
		counts[PSEUDO_EOF] = 1;
		return counts;
	}
	
	public HuffNode makeTree(int[] counts)
	{
		PriorityQueue<HuffNode> p = new PriorityQueue<>();

		for(int x = 0; x < counts.length; x++) 
		{
			if (counts[x] > 0)
			{
				p.add(new HuffNode(x, counts[x], null, null));
			}
		}

		while (p.size() > 1) {
		    HuffNode left = p.remove();
		    HuffNode right = p.remove();
		    HuffNode combo = new HuffNode(0, left.myWeight + right.myWeight, left, right);
		    p.add(combo);
		}
		return p.remove();
	}
	
	public String[] makeCodes(HuffNode root)
	{
		String[] codes = new String[ALPH_SIZE + 1];
		helpCode(codes, root, "");
		return codes;
	}
	
	public void helpCode(String[] codes, HuffNode root, String s)
	{
		HuffNode temp = root;
		if (temp == null)
		{
			return;
		}
		if (temp.myLeft == null && temp.myLeft == null)
		{
			codes[root.myValue] = s; 
			return;
		}
		helpCode(codes, root.myLeft, s + "0");
		helpCode(codes, root.myRight, s + "1");
	}
	
	public void writeTree(HuffNode root, BitOutputStream out)
	{
		HuffNode temp;
		if (!(root.myLeft == null && root.myRight == null))
		{
			out.writeBits(1, 0);
			writeTree(root.myLeft, out);
			writeTree(root.myRight, out);	
		}
		else
		{
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
		}
	}
	
	public void writeCompressedBits(String[] codes, BitInputStream in, BitOutputStream out)
	{
		for (int x = 0; x < codes.length; x++)
		{
			if (codes[x] != null && codes[x].length() > 0)
			{
				out.writeBits(codes[x].length(), Integer.parseInt(codes[x], 2));
			}
		}
		out.close();
	}
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out)
	{
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE)
		{
			throw new HuffException("illegal header starts with " + bits);
		}
		HuffNode root = readTree(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	public HuffNode readTree(BitInputStream in)
	{
		int bit = in.readBits(1);
		if (bit == -1)
		{
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		if (bit == 0)
		{
			HuffNode left = readTree(in);
			HuffNode right = readTree(in);
			return new HuffNode(0, 0, left, right);
		}
		else
		{
			return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
		}
	}
	
	public void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) 
	{
		HuffNode temp = root;
		while (true)
		{
			int bit = in.readBits(1);
			if (bit == -1)
			{
				throw new HuffException("bad input, no PSEUDO_EOF");
			}
			if (bit == 0) 
			{
				temp = temp.myLeft;
			}
			else
			{
				temp = temp.myRight;
			}
			if (temp.myLeft == null && temp.myRight == null)
			{
				if (temp.myValue == PSEUDO_EOF)
				{
					break;
				}
				else
				{
					out.writeBits(BITS_PER_WORD, temp.myValue);
					temp = root;
				}
			}
		}
	}
}