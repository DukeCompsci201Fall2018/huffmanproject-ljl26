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
		int counts[] = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codes = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root, out);
		
		in.reset();
		writeCompressedBits(codes, in, out);
		out.close();
	}
	
	private int[] readForCounts(BitInputStream in)
	{
		int[] counts = new int[ALPH_SIZE + 1];
		while (true)
		{
			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) break;
			counts[value] += 1;
		}
		counts[PSEUDO_EOF] = 1;
		if (myDebugLevel >= DEBUG_HIGH)
		{
			System.out.println("chunk\tfreq");
			for (int x = 0; x< counts.length; x++)
			{
				if (counts[x]>0)
				{
					
					System.out.printf("%d \t %d\n", x, counts[x]);
				}
			}
		}
		return counts;
	}
	
	private HuffNode makeTreeFromCounts(int[] counts)
	{
		PriorityQueue<HuffNode> p = new PriorityQueue<>();

		for(int x = 0; x < counts.length; x++) 
		{
			if (counts[x] > 0)
			{
				p.add(new HuffNode(x, counts[x], null, null));
			}
		}
		if (myDebugLevel >= DEBUG_HIGH)
		 {
			 System.out.printf("pq created with %d nodes\n", p.size());
		 }

		while (p.size() > 1) {
		    HuffNode left = p.remove();
		    HuffNode right = p.remove();
		    HuffNode combo = new HuffNode(0, left.myWeight + right.myWeight, left, right);
		    p.add(combo);
		}
		return p.remove();
	}
	
	private String[] makeCodingsFromTree(HuffNode root)
	{
		String[] codes = new String[ALPH_SIZE + 1];
		helpCode(codes, root, "");
		return codes;
	}
	
	private void helpCode(String[] codes, HuffNode root, String s)
	{
		if (root == null)
		{
			return;
		}
		if (root.myLeft == null && root.myRight == null)
		{
			codes[root.myValue] = s; 
			if (myDebugLevel>= DEBUG_HIGH)
			{
				System.out.printf("encoding for %d is %s\n", root.myValue, s);
			}
			return;
		}
		helpCode(codes, root.myLeft, s + "0");
		helpCode(codes, root.myRight, s + "1");
	}
	
	private void writeHeader(HuffNode root, BitOutputStream out)
	{
		HuffNode temp;
		if (root == null)
		{
			return;
		}
		if (!(root.myLeft == null && root.myRight == null))
		{
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);	
		}
		else
		{
			out.writeBits(1, 1);
			out.writeBits(BITS_PER_WORD + 1, root.myValue);
			if (myDebugLevel >= DEBUG_HIGH)
			{
				System.out.printf("wrote leaf for tree %d\n", root.myValue);
			}
		}
	}
	
	private void writeCompressedBits(String[] codes, BitInputStream in, BitOutputStream out)
	{
		while (true)
		{
			int value = in.readBits(BITS_PER_WORD);
			if (value == -1) break;
			out.writeBits(codes[value].length(), Integer.parseInt(codes[value], 2));
		}
		out.writeBits(codes[PSEUDO_EOF].length(), Integer.parseInt(codes[PSEUDO_EOF], 2));
		if (myDebugLevel >= DEBUG_HIGH)
		{
			System.out.println("wrote magic number " + in.readBits(BITS_PER_INT));
		}
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
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root, in, out);
		out.close();
	}
	
	private HuffNode readTreeHeader(BitInputStream in)
	{
		int bit = in.readBits(1);
		if (bit == -1)
		{
			throw new HuffException("bad input, no PSEUDO_EOF");
		}
		if (bit == 0)
		{
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			return new HuffNode(0, 0, left, right);
		}
		else
		{
			return new HuffNode(in.readBits(BITS_PER_WORD + 1), 0, null, null);
		}
	}
	
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) 
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
