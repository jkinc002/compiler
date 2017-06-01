import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.VBuiltIn.Op;
import cs132.vapor.ast.*;

import java.io.IOException;
import java.io.PrintStream;

import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.FileInputStream;

import java.util.*;

class LivenessEntry
{   
    int start = -1;
    int end = -1;
    String var = "";

    // if var is a param, this is the position in the parameter list # (e.g. for "this" = 0)
    int paramno = -1;

    LivenessEntry(int start_, int end_)
    {   
        start = start_;
        end = end_;
    }   

    LivenessEntry(int paramno_)
    {   
        paramno = paramno_;
    }   
}  

class StartComparator implements Comparator<LivenessEntry>
{
    @Override
    public int compare(LivenessEntry a, LivenessEntry b)
    {
        if (a.start < b.start)
        {
            return -1;
        }
        if (a.start > b.start)
        {
            return 1;
        }
        return 0;
    }
}

class EndComparator implements Comparator<LivenessEntry>
{
    @Override
    public int compare(LivenessEntry a, LivenessEntry b)
    {
        if (a.end < b.end)
        {
            return -1;
        }
        if (a.end > b.end)
        {
            return 1;
        }
        return 0;
    }
}
public class v2vm{

    static void printLivenessQueue(PriorityQueue<LivenessEntry> queue)
    {
        LivenessEntry le = queue.poll();
        while (le != null)
        {
            System.out.format("%s: %d-%d\n", le.var, le.start, le.end);
            le = queue.poll();
        }
        
    }

    static void printLiveness(Map<String,LivenessEntry> map)
    {
        for (Map.Entry<String, LivenessEntry> entry : map.entrySet())
        {
            String var = entry.getKey();
            LivenessEntry le = entry.getValue();
            
            System.out.format("%s: %d-%d (paramno=%d)\n", var, le.start, le.end, le.paramno);
        }
    }

    public static Map<String,LivenessEntry> getLiveness(VFunction func) {
    
        VaporVisitor<ProblemException> vaporVisitor = new VaporVisitor<ProblemException>();
        Map<String, LivenessEntry> livenessMap = new HashMap<String, LivenessEntry>();
        Vector<String> params = new Vector<String>();

        for (VVarRef.Local param : func.params)
        {
            params.add(param.ident);
        }

        for (VInstr instr : func.body)
        {

            int lineno = instr.sourcePos.line;
            VaporReturn instrRet = null;
            try {
                instrRet = instr.accept(new VaporReturn(), vaporVisitor);
            } catch (ProblemException e)
            {
                System.err.println("Error visiting instruction:");
                e.printStackTrace();
                break;
            }
            
            for (String var : instrRet.vars)
            {
                LivenessEntry livenessEntry = new LivenessEntry(lineno, lineno);
                livenessEntry = livenessMap.getOrDefault(var, livenessEntry);
                livenessEntry.end = lineno;
                livenessEntry.paramno = params.indexOf(var);
                livenessMap.put(var, livenessEntry);
            }
        }
        return livenessMap;
    }



    public static void main (String[] args)
    {
        VaporProgram vp;
        try {
            vp = parseVapor(System.in, System.err);    
        }
        catch (IOException e)
        {
            e.printStackTrace();
            return;
        }
    
        for (VFunction func : vp.functions)
        {
            // gets the liveness map of the current function
            Map<String, LivenessEntry> livenessMap = v2vm.getLiveness(func);
            //printLiveness(livenessMap);

            // map our vars to registers/spill
            Comparator<LivenessEntry> startComparator = new StartComparator();
            PriorityQueue<LivenessEntry> startQueue = new PriorityQueue<LivenessEntry>(startComparator);
            Comparator<LivenessEntry> endComparator = new EndComparator();
            PriorityQueue<LivenessEntry> endQueue = new PriorityQueue<LivenessEntry>(endComparator);
            
            for (Map.Entry<String,LivenessEntry> entry : livenessMap.entrySet())
            {
                String var = entry.getKey();
                LivenessEntry le = entry.getValue();
                le.var = var;
                if (le.paramno == -1)
                {
                    startQueue.add(le);
                }
            }
           
            System.out.format("-----%s-----\n", func.index);
            printLivenessQueue(startQueue);

            // visit a second time, this time generating code
            // from computed spill/registers for our vars

            
            


        }
    }

    public static VaporProgram parseVapor(InputStream in, PrintStream err) throws IOException {
      Op[] ops = {
          Op.Add, Op.Sub, Op.MulS, Op.Eq, Op.Lt, Op.LtS,
          Op.PrintIntS, Op.HeapAllocZ, Op.Error,
      };
      boolean allowLocals = true;
      String[] registers = null;
      boolean allowStack = false;

      VaporProgram tree;
      try {
          tree = VaporParser.run(new InputStreamReader(in), 1, 1,
                               java.util.Arrays.asList(ops),
                               allowLocals, registers, allowStack);
      }
      catch (ProblemException ex) {
          err.println(ex.getMessage());
          return null;
      }

      return tree;
    }

}
