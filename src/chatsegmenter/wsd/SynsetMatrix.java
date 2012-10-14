package chatsegmenter.wsd;
import java.util.*;

import net.didion.jwnl.*;
import net.didion.jwnl.data.*;

import chatsegmenter.LexChain;
import chatsegmenter.helper.RelParent;
import chatsegmenter.helper.UttTaggedSenseWord;
import chatsegmenter.helper.UttTaggedWord;
import chatsegmenter.helper.WordNet;
import chatsegmenter.helper.WordNetHelper;


public class SynsetMatrix {
	private int[][] matrix;
	
	private Vector<UttTaggedSenseWord> wordSenses;
	private Vector<UttTaggedWord> words;
	private Vector<Integer> pos;
	private Vector<Vector<Synset>> senseList;
	private Vector<Vector<Integer>> valid;
	private Vector<Integer> grad;
	public static Hashtable<Long, Integer> senseCache;
	
	static
	{
		senseCache = new Hashtable<Long, Integer>();
	}
	
	class SynsetExt 
	{
		private Synset sense;
		private int grad;
		private int posx;
		private int posy;
		
		public SynsetExt(Synset sense, int grad, int posx, int posy)
		{
			this.sense = sense;
			this.grad = grad;
			this.posx = posx;
			this.posy = posy;
		}
		
		public int getPositionX()
		{
			return posx;
		}
		
		public int getPositionY()
		{
			return posy;
		}
		
		public Synset getSense()
		{
			return sense;
		}
		
		public int getGrad()
		{
			return grad;
		}

	}
	class MyComp implements Comparator
	{
		public int compare(Object o1, Object o2)
		{
			if (((SynsetExt)o1).getGrad() < ((SynsetExt)o2).getGrad())
				return 1;
			if (((SynsetExt)o1).getGrad() > ((SynsetExt)o2).getGrad())
				return -1;
			return 0;
		}
	}
	public SynsetMatrix(Vector<UttTaggedWord> words) throws JWNLException
	{
		this.words = words;
		wordSenses = new Vector<UttTaggedSenseWord>();
		pos = new Vector<Integer>();
		senseList = new Vector<Vector<Synset>>();
		valid = new Vector<Vector<Integer>>();
		int index = 0;
		pos.add(index);
		
		if (LexChain.DEBUG_PRINT)
			System.out.println(words.size());
		
		for (int i = 0; i < words.size(); i++)
		{
			if (LexChain.DEBUG_PRINT)
				System.out.println("getting senses for " + i);
			
			UttTaggedWord word = words.elementAt(i);
			Synset[] senses = word.getIndexWord().getSenses();
			senseList.add(new Vector<Synset>());
			for (int j = 0; j < senses.length; j++)
			{
				wordSenses.add(new UttTaggedSenseWord(word, senses[j]));
				senseList.elementAt(i).add(senses[j]);
			}
			index = index + senses.length;
			pos.add(index);
		}
		int n = wordSenses.size();
		
		if (LexChain.DEBUG_PRINT)
			System.out.println(n);
		
		matrix = new int[n][n];
		grad = new  Vector<Integer>();
		for (int i = 0; i < n; i++)
			grad.add(0);
		
		for (int i = 0; i < senseList.size(); i++)
		{
			valid.add(new Vector<Integer>());
			for (int j = 0; j < senseList.elementAt(i).size(); j++)
			{
				valid.elementAt(i).add(1);
			}
		}
		for (int i = 0; i < n - 1; i++)
		{
			for (int j = i + 1; j < n; j++)
			{	
				if (LexChain.DEBUG_PRINT)
					System.out.println("get rel for "+ i + " " + j);
				
				RelParent rel = WordNetHelper.getInstance().areRelated(wordSenses.elementAt(i).getSense(), wordSenses.elementAt(j).getSense());
				matrix[i][j] = rel.getLength();
				matrix[j][i] = matrix[i][j];
				if (matrix[i][j] < 100)
				{
					grad.set(i, grad.elementAt(i) + 1);
					grad.set(j, grad.elementAt(j) + 1);	
				}
			}
		}
		if (words.size() > 10)
		selectMax(3);
		else selectMax(4);
	}
	private void selectMax(int limit)
	{

		for (int i = 0; i < senseList.size(); i++)
		{
			Vector<Synset> senses = senseList.elementAt(i);
			Vector<SynsetExt> sensesExt = new Vector<SynsetExt>();
			for (int j = 0; j < senses.size(); j++)
			{
				sensesExt.add(new SynsetExt(senses.elementAt(j),getGrad(i, j), i, j));
			}
			Collections.sort(sensesExt, new MyComp());
			
			if (sensesExt.elementAt(0).getGrad() == 0)
			{
				int k = 0;
				for (int j = 0; j < senses.size(); j++)
				{
					valid.elementAt(i).set(sensesExt.elementAt(j).getPositionY(), 0);
					if (senseCache.get(senses.elementAt(j).getOffset()) != null)
					{
						k = j;
					}
				}
				valid.elementAt(i).set(sensesExt.elementAt(k).getPositionY(), 1);
			}
			else
			{
				for (int j = Math.min(limit, sensesExt.size()); j < sensesExt.size(); j++)
				{
					SynsetExt se = sensesExt.elementAt(j);
					valid.elementAt(i).set(se.getPositionY(), 0);
				}
			}
			
		}
	}
	public int getPos(int wordIndex,int senseIndex)
	{
		return pos.elementAt(wordIndex) + senseIndex;
	}
	
	public int getRelLength(int wordIndex1,int senseIndex1, int wordIndex2,int senseIndex2)
	{
		int pos1 = pos.elementAt(wordIndex1) + senseIndex1;
		int pos2 = pos.elementAt(wordIndex2) + senseIndex2;
		return matrix[pos1][pos2];
	}
	public int getGrad(int wordIndex,int senseIndex)
	{
		return grad.elementAt(pos.elementAt(wordIndex)+senseIndex);
	}
	public Vector<Integer> getLengths(int wordIndex,int senseIndex)
	{
		int position = getPos(wordIndex, senseIndex);
		Vector<Integer> len = new Vector<Integer>();
		for (int i = 0;i < wordSenses.size(); i++)
		{
			if (matrix[position][i] < 100)
			{
				len.add(matrix[position][i]);
			}
		}
		
		return len;
		
	}
	public Vector<Vector<Synset>> getSenseList()
	{
		return senseList;
	}
	public UttTaggedWord getWord(int wordIndex,int senseIndex)
	{
		return wordSenses.elementAt(pos.elementAt(wordIndex)+senseIndex);
	}
	public Vector<Vector<Integer>> getValid()
	{
		return valid;
	}
	
}
