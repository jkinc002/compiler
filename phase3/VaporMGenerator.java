import java.util.Vector;

import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.*;
import cs132.vapor.ast.VAddr;

import java.util.regex.Pattern;
import java.util.*;

// Visitor's return class
class VaporMReturn {
    int instrLine;
    VaporMReturn(int lineno)
    {
        this.instrLine = lineno;
    }
    VaporMReturn()
    {
        this.instrLine = 0;
    }
}

// Class to help print with indents
class Printer {
    int scope;
    Printer(){
        this.scope = 0;
    }
    void printScope()
    {
        for(int i=0;i<scope;++i)
        {
            System.out.print("  ");
        }
    }
    void println(String s)
    {
        printScope();
        System.out.println(s);
    }

    void print(String s)
    {
        System.out.print(s);
    }

    void decreaseScope()
    {
        scope = Math.max(0,--scope);
    }
    void increaseScope()
    {
        ++scope;
    }
}

// Visitor
public class VaporMGenerator<E extends Throwable> extends VInstr.VisitorPR<VaporMReturn,VaporMReturn,E> {
    
    RegisterAllocation regAlloc;
    Map<String,String> regMap;
    RegisterPool regPool;
    VFunction func;
    Map<String,Integer> params;
    Printer printer;

    // Initialize VaporM code generator
    VaporMGenerator(RegisterAllocation regAlloc_, int out) {
        regAlloc = regAlloc_;
        regMap = regAlloc.registerMap;
        regPool = regAlloc.registerPool;
        params = new HashMap<String,Integer>();

        // Print function header
        func = regAlloc.func;
        int in = func.params.length;
        int local = regPool.maxSRegs + regPool.maxTRegs + regAlloc.spillCount;
        System.out.format("func %s [in %d, out %d, local %d]\n", func.ident, in, out, local);
        
        this.printer = new Printer();
        this.printer.increaseScope();

        // Record function parameters
        int paramno = 0;
        for (VVarRef.Local param : func.params)
        {
            params.put(param.ident, paramno);
            paramno++;
        }

        // Save 's' registers at beginnign of function
        for (int i = 0; i < regPool.maxSRegs; ++i)
        {
            int localIndex = i + regAlloc.spillCount;
            printer.println(String.format("local[%d] = $s%d", localIndex, i));
        }  
    }
    
    // Generate handoff code for stack locations
    String getStackReg(String var, String vReg)
    {
        String reg = getReg(var);
        if (reg.charAt(reg.length()-1) == ']')
        {
            printer.println(vReg + " = " + reg);
            reg = vReg;
        }
        return reg;
    }

    // Get the VaporM register associated with the Vapor variable
    String getReg(String var)
    {
    
        int paramno = params.getOrDefault(var, -1);
        if (paramno != -1)
        {
            if (paramno < 4)
            {
                return String.format("$a%d", paramno);
            }
            else
            {
                return "in[" + Integer.toString(paramno) + "]";
            }
        }
        else
        {
            // return the reg for variables
            // return the var itself for labels and numbers
            return regMap.getOrDefault(var, var);
        }
    }

    /*
     * VVarRef      dest        The location being stored to.
     * VOperand     source      The value being stored
     */
    public VaporMReturn visit(VaporMReturn args, VAssign n)
    {
        VaporMReturn vaporMReturn = new VaporMReturn();

        // Get registers or stack location of 'source' and 'dest'
        String source = getStackReg(n.source.toString(), "$v0");
        String dest = getReg(n.dest.toString());

        // Print line
        printer.println(dest + " = " + source);
        return vaporMReturn;
    }

    /*
     * boolean                  positive    (if = 1, if0 = 0)
     * VLabelRef<VCodeLabel>    target      The label to go to if branch is taken
     * VOperand                 value       The value being branched on
     */
    public VaporMReturn visit(VaporMReturn args, VBranch n)
    {
        VaporMReturn vaporMReturn = new VaporMReturn();
        String ifStr;
        // Get correct 'if' type ('if' or 'if0')
        if(n.positive)
            ifStr = "if";
        else
            ifStr = "if0";

        // Get registers or stack locations of 'value' and 'target'
        String value = getStackReg(n.value.toString(), "$v0");
        String target = getReg(n.target.ident);

        // Print line
        printer.println(ifStr + " " + value + " goto :" + target);

        return vaporMReturn;
    }

    /*
     * VOperand[]   args        The arguments
     * VVarRef      dest        Variable in which to store result
     * VBuiltIn.Op  op          The operation being performed (Add, Sub, etc)
     */
    public VaporMReturn visit(VaporMReturn args, VBuiltIn n)
    {
        VaporMReturn vaporMReturn = new VaporMReturn();

        // Create String[] for arguments of operation
        String[] argsStr = new String[n.args.length];
        for (int i = 0; i < n.args.length; ++i)
        {
            String argStr = getReg(n.args[i].toString());
            // Handle stack arguments
            if(argStr.charAt(argStr.length()-1) == ']')
            {
                printer.println("$v" + i + " = " + argStr);
                argStr = "$v" + i;
            }
            argsStr[i] = argStr;
        }
        printer.printScope();

        // Build argument String to be inside parenthesis
        String s = "";
        for (String arg : argsStr)
        {
           s = s + arg + ' ';
        }
        s = s.substring(0,s.length() - 1); //delete trailing space

        
        VVarRef.Local dest = (VVarRef.Local) n.dest;
        // Case where destination is assigned the operation result
        if(dest != null)
        {
            String destReg = getReg(dest.ident);
            // Handle stack destinations
            if(destReg.charAt(destReg.length()-1) == ']')
            {
                printer.print("$v0 = " + n.op.name + '(' + s + ")\n");
                printer.print(destReg + " = $v0\n");
            }
            // Register destinations
            else
            {
                printer.print(destReg + " = " + n.op.name + '('+ s + ")\n");
            }
        }
        // Case where there is no destination being assigned to
        else
        {
        printer.print(n.op.name + '(');
        printer.print(s + ")\n");
        }

        return vaporMReturn;
    }

    public Vector<String> getLiveTRegs(int instrLine) {
        Vector<String> liveTRegs = new Vector<String>();
        
        //TODO make a map of only treg liveness
        for (Map.Entry<String, LivenessEntry> livenessMapEntry : regAlloc.livenessMap.entrySet())
        {
            String reg = getReg(livenessMapEntry.getKey());
            LivenessEntry le = livenessMapEntry.getValue();
            
            if (reg.startsWith("$t"))
            {
                if (le.start <= instrLine && instrLine <= le.end)
                {
                    liveTRegs.add(reg);
                }
            }
        }
        return liveTRegs;
    } 

    /*
     * Vaddr<VFunction>     addr    The address of func being called
     * VOperand[]           args    The list of args passed into function
     * VVarRef.Local        dest    The var used to store return value
     */
    public VaporMReturn visit(VaporMReturn args, VCall n)
    {
        String dest = getStackReg(n.dest.toString(), "$v1");

        //Save 't' registers before function call
        Vector<String> tregs = getLiveTRegs(n.sourcePos.line);
        for (String reg : tregs)
        {
            int regno = Character.getNumericValue(reg.charAt(2));
            //System.out.println("-----Regno: "+ regno + "--------");
            int localIndex = regAlloc.spillCount + regPool.maxSRegs + regno;
            printer.println(String.format("local[%d] = %s", localIndex, reg));
        }

        // Print 'a' register assignment lines
        
        // saves a0..a3 into in[0]..in[3]
        Set<String> savedARegs = new HashSet<String>();
        for (int argno = 0; argno < params.size() && argno < n.args.length && argno < 4; ++argno)
        {
            printer.println(String.format("in[%d] = $a%d",argno,argno));
            savedARegs.add(String.format("$a%d",argno));
        }
        
        // populates out for args > 3
        for (int argno = 4; argno < n.args.length; ++argno)
        {
            String sarg = getStackReg(n.args[argno].toString(), "$v0");
            printer.println(String.format("out[%d] = %s", argno, sarg));
        }

        // populate $a regs, replacing assignments from $ax with in[x]
        for (int argno = 0; argno < n.args.length && argno < 4; ++argno)
        {
            String sarg = getReg(n.args[argno].toString());
            if(savedARegs.contains(sarg))
            {
                //printer.println("----Sarg: " + sarg + "------");
                sarg = String.format("in[%s]",sarg.charAt(2));
            } 
            printer.println(String.format("$a%d = %s", argno, sarg));
        }

        // Print call instruction
        String addr = getStackReg(n.addr.toString(), "$v0");
        printer.println(String.format("call %s", addr));
        
        // Reload 'a' registers
        
        for (int argno = 0; argno < 4 && argno < n.args.length && argno < params.size(); ++argno)
        {
            printer.println(String.format("$a%d = in[%d]", argno, argno));
        }

        // Reload 't' registers
        for (String reg : tregs)
        {
            int regno = Character.getNumericValue(reg.charAt(2));
            int localIndex = regAlloc.spillCount + regPool.maxSRegs + regno;
            printer.println(String.format("%s = local[%d]", reg, localIndex));
        } 
        
        // Print return line '$x = $v0'
        printer.println(String.format("%s = $v0", dest));
        String destOrig = getReg(n.dest.toString());

        // Handle returns that are put into Stack Location
        if(destOrig.charAt(destOrig.length()-1) == ']')
        {
           printer.println(destOrig + " = " + dest);
        }

        

        VaporMReturn vaporMReturn = new VaporMReturn();
        return vaporMReturn; 
    }

    /*
     *  VAddr<VCodeLabel> target    The target of the jump
     */
    public VaporMReturn visit(VaporMReturn args, VGoto n)
    {
        String target = getReg(n.target.toString());
        printer.println("goto " + target);
        return new VaporMReturn();
    }

    /*
     *  VVarRef     dest        The var/reg to store the value
     *  VMemRef     source      The memory location being read
     */
    public VaporMReturn visit(VaporMReturn args, VMemRead n)
    {
        VaporMReturn vaporMReturn = new VaporMReturn();
        // Get register or stack location associated with dest and source
        String dest = getReg(n.dest.toString());
        VMemRef.Global source = (VMemRef.Global) n.source;
        String strsource = getStackReg(source.base.toString(), "$v1");
        String index = Integer.toString(source.byteOffset);
        // Case where dest has a stack location
        if(dest.charAt(dest.length()-1) == ']')
        {
            printer.println("$v0 = [" + strsource + '+' + index + "]");
            printer.println(dest + " = $v0");
        }
        // Case where dest has a register
        else
            printer.println(dest + " = [" + strsource + '+' + index + ']');

        return vaporMReturn;
    }

    /*
     *  VOperand    source    The value being written
     *  VMemRef     dest      The memory location being written to
     */    
    public VaporMReturn visit(VaporMReturn args, VMemWrite n)
    {   
        VaporMReturn vaporMReturn = new VaporMReturn();
        VMemRef.Global dest = (VMemRef.Global) n.dest;
        String index = Integer.toString(dest.byteOffset);
        
        // Get register or stack location associated with 'source' and 'dest'
        String source = getStackReg(n.source.toString(), "$v0");
        String destStr = getStackReg(dest.base.toString(), "$v1");

        // Print line
        printer.println('[' + destStr +'+'+ index + "] = " + source);

        return vaporMReturn;
    }

    /*
     *  VOperand    value   The value being returned
     */
    public VaporMReturn visit(VaporMReturn args, VReturn n){
        VaporMReturn vaporMReturn = new VaporMReturn();
        // Load 's' registers
        for (int i = 0; i < regPool.maxSRegs; ++i)
        {
            int localIndex = regAlloc.spillCount + i;
            printer.println(String.format("$s%d = local[%d]", i, localIndex));
        }
        
        // Handle case where value is being returned
        if(n.value != null)
        {
            String value = n.value.toString();
            printer.println("$v0 = " + getReg(value));
        }
        printer.println("ret\n");

        return vaporMReturn;
    }


}
