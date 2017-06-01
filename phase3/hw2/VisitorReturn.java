import java.util.Vector;

public class VisitorReturn {
    public String type;
    public Vector<String> tmps;
    private Vector<String> labels;
    public boolean isField = false;

    public void setField()
    {
        this.isField = true;
    }

    public VisitorReturn(String type) 
    {
        this.type = type;
        tmps = new Vector<String>();
        labels = new Vector<String>();
    }

    public VisitorReturn()
    {
        this.type = null;
        tmps = new Vector<String>();
        labels = new Vector<String>();
    }

    public VisitorReturn(String t, String temp)
    {
        this.type = t;
        tmps = new Vector<String>();
        this.addTmp(temp);
        labels = new Vector<String>();
    }

    public static VisitorReturn tmpReturn(String tmp)
    {
       VisitorReturn ret = new VisitorReturn();
       ret.tmps.add(tmp);
       return ret;
    }

    public void setType(String t)
    {
        this.type = t;
    }

    public String getType()
    {
        return this.type;
    }

    public String getTmp(int i) 
    {
        return tmps.get(i);
    }

    public void addTmp(String tmp)
    {
        tmps.add(tmp);
    }

    public String getTmp() {
        return getTmp(0);
    }

    public String getLabel(int i)
    {
        return labels.get(i);
    }

    public String getLabel()
    {
        return getLabel(0);
    }

    public void addLabel(String label)
    {
        labels.add(label);
    }
}
