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
import java.util.Collections.*;

class RegisterPool 
{
    String[] tRegisters = {"$t8", "$t7", "$t6", "$t5", "$t4", "$t3", "$t2", "$t1", "$t0"};
    String[] sRegisters = {"$s7", "$s6", "$s5", "$s4", "$s3", "$s2", "$s1", "$s0"};

    Stack<String> tRegs;
    Stack<String> sRegs;

    int RegCount;
    int maxSRegs = 0;
    int maxTRegs = 0;

    RegisterPool()
    {
        tRegs = new Stack<String>();
        sRegs = new Stack<String>();
        for (int i=0; i<8; ++i)
        {
            tRegs.push(tRegisters[i]);
            sRegs.push(sRegisters[i]);
        }
        tRegs.push(tRegisters[8]);
        RegCount = 17;
    }

    String getReg()
    {

        //FIXME: update call map
        // intuition: a reg gotten after a call instr means
        //  that reg must be saved. Other regs are not
        //  saved even if they were used before the call instr
        if(!tRegs.empty())
        {
            RegCount -= 1;
            String tReg = tRegs.pop();
            maxTRegs = Math.max(maxTRegs, 9 - tRegs.size());
            return tReg;
        }
        else if(!sRegs.empty())
        {
            RegCount -= 1;
            String sreg = sRegs.pop();
            maxSRegs = Math.max(maxSRegs, 8 - sRegs.size());
            return sreg;
        }
        return "";
    }

    void freeRegister(String reg)
    {
       if(reg.charAt(1) == 't')
       {
            tRegs.push(reg);
            ++RegCount;
       } 
       else if(reg.charAt(1) == 's')
       {
            sRegs.push(reg);
            ++RegCount;
       }
       else{
            System.err.println("ERROR: freed register is invalid");
            System.exit(1);
       }

    }

}

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

class RegisterAllocation {
    RegisterPool registerPool;
    LinkedList<LivenessEntry> activeIntervals;
    PriorityQueue<LivenessEntry> newIntervals;
    Map<String,String> registerMap;
    Map<String, LivenessEntry> livenessMap;
    VFunction func;
    int spillCount;

    //Jordan
    HashMap<Integer,Integer> callMap; //maps call instruction line numbers to the number of 
                                      // 't' registers being used below them


    static final Comparator<LivenessEntry> startComparator;
    static final Comparator<LivenessEntry> endComparator;

    static {
        startComparator = new StartComparator();
        endComparator = new EndComparator();
    }

    RegisterAllocation(Map<String, LivenessEntry> livenessMap, VFunction func_) {
        
        registerPool = new RegisterPool();
        activeIntervals = new LinkedList<LivenessEntry>();
        newIntervals = new PriorityQueue<LivenessEntry>(startComparator);
        registerMap = new HashMap<String,String>();
        func = func_;
        spillCount = 0; 
        this.livenessMap = livenessMap;
        

        for (Map.Entry<String,LivenessEntry> entry : livenessMap.entrySet())
        {
            String var = entry.getKey();
            LivenessEntry le = entry.getValue();
            le.var = var;
            if (le.paramno == -1)
            {
                newIntervals.add(le);
            }
        }

    }
    
    void linearScanRegisterAlloc()
    {

        LivenessEntry le = newIntervals.poll();
        final int R = 17;
        //for live interval i, in order of increasing start point
        while (le != null)
        {
            expireOldIntervals(le);
            if (activeIntervals.size() == R)
            {
                spillAtInterval(le);
            }
            else
            {
                String reg = registerPool.getReg(); //$t0
                activeIntervals.add(le);
                registerMap.put(le.var,reg);
            }
            le = newIntervals.poll();
        }
    }

    void spillAtInterval(LivenessEntry newEntry)
    {
        LivenessEntry lastActive = activeIntervals.peekLast();
        LivenessEntry spill;
        if(lastActive.end > newEntry.end)
        {
            registerMap.put(newEntry.var, registerMap.get(lastActive.var));
            activeIntervals.removeLast();
            activeIntervals.addLast(newEntry);
            Collections.sort(activeIntervals, endComparator); //FIXME: insertion sort instead

            spill = lastActive;
        }
        else
        {
            spill = newEntry; 
        }
        registerMap.put(spill.var, "local[" + Integer.toString(spillCount) + "]");
        spillCount++;
    }

    void expireOldIntervals(LivenessEntry newEntry)
    {
        LivenessEntry activeEntry = activeIntervals.peekFirst();
        while (activeEntry != null && activeEntry.end < newEntry.start) {
            String reg = registerMap.get(activeEntry.var);
            registerPool.freeRegister(reg);
            activeIntervals.removeFirst();

            activeEntry = activeIntervals.peekFirst();
        }
    }
}

class LivenessReturn {
        Map<String,LivenessEntry> livenessMap;
        int maxOut;

        LivenessReturn(Map<String,LivenessEntry> livenessMap_, int maxOut_)
        {
            livenessMap = livenessMap_;
            maxOut = maxOut_;
        }
    }

public class V2VM{

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
    
    

    public static LivenessReturn getLiveness(VFunction func) {
    
        VaporVisitor<ProblemException> vaporVisitor = new VaporVisitor<ProblemException>();
        Map<String, LivenessEntry> livenessMap = new HashMap<String, LivenessEntry>();
        Vector<String> params = new Vector<String>();

        for (VVarRef.Local param : func.params)
        {
            params.add(param.ident);
        }
        
        int maxOut = 0;
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
            maxOut = Math.max(maxOut, instrRet.outSize);


            for (String var : instrRet.vars)
            {
                LivenessEntry livenessEntry = new LivenessEntry(lineno, lineno);
                livenessEntry = livenessMap.getOrDefault(var, livenessEntry);
                livenessEntry.end = lineno;
                livenessEntry.paramno = params.indexOf(var);
                livenessMap.put(var, livenessEntry);
            }
        }
        return new LivenessReturn(livenessMap, maxOut);
    }

    
    static void printVTables(VaporProgram vp)
    {
        for (VDataSegment seg : vp.dataSegments)
        {
            String constOrVar = seg.mutable? "var" : "const";
            System.out.println(constOrVar + " " + seg.ident);

            for (VOperand.Static val: seg.values)
            {
                System.out.println("  " + val);
            }

            System.out.println();
        }
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
   
        printVTables(vp);

        for (VFunction func : vp.functions)
        {
            // gets the liveness map of the current function
            LivenessReturn livenessReturn = getLiveness(func);
            Map<String, LivenessEntry> livenessMap = livenessReturn.livenessMap;
            int maxOut = livenessReturn.maxOut;
            //printLiveness(livenessMap);

            // map our vars to registers/spill
            RegisterAllocation registerAllocation = new RegisterAllocation(livenessMap, func);
            registerAllocation.linearScanRegisterAlloc();

            //visit a second time to get callMap
           
            // visit a third time, this time generating code
            // from computed spill/registers for our vars
            VaporMGenerator<ProblemException> vaporMGenerator = new VaporMGenerator<ProblemException>(registerAllocation, maxOut);
            int funcLine = func.sourcePos.line;
            int nextLabelIndex = 0;
            int labelOffset = 0;
            int finalLineno= 0;
            VCodeLabel nextLabel = null;
            if (func.labels.length > 0)
            {
                nextLabel = func.labels[0];
                finalLineno = nextLabel.instrIndex + labelOffset;
            }
            /*
            System.out.println("----------------------");
            int j = 0;
            for(VCodeLabel label : func.labels)
            {
                System.out.println(Integer.toString(label.instrIndex + j) + ' ' + label.ident);
                ++j;
            }
            System.out.println("----------------------");
            */
            for (VInstr instr : func.body)
            {
                int instrIndex = instr.sourcePos.line - funcLine - 1;
                while (nextLabel != null && finalLineno < instrIndex)
                {
                    //print label
                    //System.out.print(Integer.toString(finalLineno) + ' ');  //83
                    System.out.println(nextLabel.ident + ":");
                   
                    //update nextLabel
                    nextLabelIndex++;
                    ++labelOffset;
                    if (nextLabelIndex < func.labels.length)
                    {
                       nextLabel = func.labels[nextLabelIndex];
                       finalLineno = nextLabel.instrIndex + labelOffset;
                    }
                    else
                    {
                        nextLabel = null;
                    }
                }
                //System.out.print(Integer.toString(instr.sourcePos.line - funcLine - 1)); //82
                
                
                
                try {
                    //FIXME: add functionality to tell what registers are no longer used
                    //       after this instruction.
                    //
                    //VaporM Generator for individual instruction
                    instr.accept(new VaporMReturn(instr.sourcePos.line), vaporMGenerator);
                } catch (ProblemException e)
                {
                    System.err.println("Error visiting instruction:");
                    e.printStackTrace();
                    break;
                }
            }
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
