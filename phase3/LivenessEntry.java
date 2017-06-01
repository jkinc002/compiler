public class LivenessEntry
{   
    int start;
    int end;
    int paramno = -1; //can't operate on parameters

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
