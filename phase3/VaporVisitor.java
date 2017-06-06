import java.util.Vector;

import cs132.util.ProblemException;
import cs132.vapor.parser.VaporParser;
import cs132.vapor.ast.*;
import cs132.vapor.ast.VAddr;

import java.util.regex.Pattern;

class VaporReturn {
    Vector<String> vars;
    private static final Pattern varPattern;
    int outSize = 0;
    Boolean isCall;

    static {
       varPattern = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_.]*");
    }

    VaporReturn(int size)
    {
        vars = new Vector<String>(size);
        isCall = false;
    }

    public void addVar(String var)
    {
        //is actually a var or param
        if (var != null && VaporReturn.varPattern.matcher(var).matches())
        {
            vars.add(var);
        }
    }
    VaporReturn(){vars = new Vector<String>();}
}

public class VaporVisitor<E extends Throwable> extends VInstr.VisitorPR<VaporReturn,VaporReturn,E> {
    
    /*
     * VVarRef      dest        The location being stored to.
     * VOperand     source      The value being stored
     */
    public VaporReturn visit(VaporReturn args, VAssign n)
    {
        VaporReturn vaporReturn = new VaporReturn();
        vaporReturn.addVar(n.source.toString());
        vaporReturn.addVar(n.dest.toString());
        return vaporReturn;
    }

    /*
     * boolean                  positive    (if = 1, if0 = 0)
     * VLabelRef<VCodeLabel>    target      The label to go to if branch is taken
     * VOperand                 value       The value being branched on
     */
    public VaporReturn visit(VaporReturn args, VBranch n)
    {
        VaporReturn vaporReturn = new VaporReturn();
        vaporReturn.addVar(n.value.toString());
        return vaporReturn;
    }

    /*
     * VOperand[]   args        The arguments
     * VVarRef      dest        Variable in which to store result
     * VBuiltIn.Op  op          The operation being performed (Add, Sub, etc)
     */
    public VaporReturn visit(VaporReturn args, VBuiltIn n){
        VaporReturn vaporReturn = new VaporReturn();
        if (n.dest != null)
            vaporReturn.addVar(n.dest.toString());
        for (int i = 0; i < n.args.length; ++i)
        {
            vaporReturn.addVar(n.args[i].toString());
        }
        
        return vaporReturn;
    }

    /*
     * Vaddr<VFunction>     addr    The address of func being called
     * VOperand[]           args    The list of args passed into function
     * VVarRef.Local        dest    The var used to store return value
     */
    public VaporReturn visit(VaporReturn args, VCall n)
    {
        
        VaporReturn vaporReturn = new VaporReturn();
        vaporReturn.isCall = true;

        vaporReturn.outSize = n.args.length;
        if (n.addr instanceof VAddr.Var)
        {
            vaporReturn.addVar(((VAddr.Var)n.addr).var.toString());
        }
        vaporReturn.addVar(n.dest.toString());
        for(int i = 0; i < n.args.length; ++i)
        {
            vaporReturn.addVar(n.args[i].toString());
        }
        return vaporReturn; 
    }

    /*
     *  VAddr<VCodeLabel> target    The target of the jump
     */
    public VaporReturn visit(VaporReturn args, VGoto n)
    {
        return new VaporReturn();
    }

    /*
     *  VVarRef     dest        The var/reg to store the value
     *  VMemRef     source      The memory location being read
     */
    public VaporReturn visit(VaporReturn args, VMemRead n)
    {
        VaporReturn vaporReturn = new VaporReturn();
        vaporReturn.addVar(n.dest.toString());
        VMemRef.Global source = (VMemRef.Global) n.source;
        if (source.base instanceof VAddr.Var)
        {
            vaporReturn.addVar(source.base.toString());
        }


        return vaporReturn;
    }

    /*
     *  VOperand    source    The value being written
     *  VMemRef     dest      The memory location being written to
     */    
    public VaporReturn visit(VaporReturn args, VMemWrite n)
    {   
        VaporReturn vaporReturn = new VaporReturn();
        vaporReturn.addVar(n.source.toString());
        
        VMemRef.Global dest = (VMemRef.Global) n.dest;
        if (dest.base instanceof VAddr.Var)
        {
            vaporReturn.addVar(dest.base.toString());
        }
        return vaporReturn;
    }





    /*
     *  VOperand    value   The value being returned
     */
    public VaporReturn visit(VaporReturn args, VReturn n){
        VaporReturn vaporReturn = new VaporReturn();
        if (n.value != null)
            vaporReturn.addVar(n.value.toString());
        return vaporReturn;
    }


}
