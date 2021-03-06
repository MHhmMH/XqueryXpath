import java.io.File;
import java.util.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class XQueryExtender extends XQueryBaseVisitor<ArrayList<Node>> {
    private boolean hasAttribute = false;
    private Document inputDoc = null;
    Document outputDoc = null;
    private HashMap<String, ArrayList<Node>> contextMap = new HashMap<>();
    private ArrayList<Node> current_list = new ArrayList<>();
    boolean DoReWrite = true;

    @Override
    public ArrayList<Node> visitXqAp(XQueryParser.XqApContext context) {
        return visit(context.ap());
    }

    @Override
    public ArrayList<Node> visitXqTag(XQueryParser.XqTagContext context) {
        if (outputDoc == null){
            try {
                DocumentBuilderFactory docBF = DocumentBuilderFactory.newInstance();
                DocumentBuilder docB = docBF.newDocumentBuilder();
                outputDoc = docB.newDocument();
            } catch (ParserConfigurationException e) {
                e.printStackTrace();
            }
        }
        ArrayList<Node> result = new ArrayList<>();

        ArrayList<Node> xqRes = visit(context.xq());

        // System.out.println("xqRes size: " + xqRes.size());
        // for (Node n : xqRes) {
        //     System.out.println(n.getNodeName());
        // }

        result.add(createNode(context.NAME(0).getText(), xqRes));

        // System.out.println("result size: " + result.size());
//         for (Node n : result) {
//             System.out.println("result node name: " + n.getNodeName());
//             System.out.println("result node children size: " + n.getChildNodes().getLength());
//             System.out.println("First node name: " + n.getFirstChild().getNodeName());
//             System.out.println("Last node name: " + n.getLastChild().getNodeName());
//         }
        return result;
    }

    private void permute(XQueryParser.FLWRContext context, int k, ArrayList<Node> result){
        if (k == context.forClause().var().size()){
            HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
            if (context.letClause() != null) {
                visit(context.letClause());
            }
            if (context.whereClause() != null) {
                if(visit(context.whereClause()).size()==0){
                    return;
                }
            }
            ArrayList<Node> c = visit(context.returnClause());
            //System.out.println("c: " + c.size());
            if (c != null) {
                result.addAll(visit(context.returnClause()));
            }
            contextMap = contextMapOld;
            // System.out.println("Permute:" + result.size());
            // for (Node n: result) {
            //     System.out.println("permuteNode: " + n.getNodeName());
            // }
        }
        else {
            String var = context.forClause().var(k).getText();
            // System.out.println("forClause.getText(): " + var);
            ArrayList<Node> varNodes = visit(context.forClause().xq(k));
            // System.out.println("varNode: " + varNodes.size());
            for (Node temp : varNodes){
                // System.out.println("temp node name:" + temp.getTextContent());
                contextMap.remove(var);
                ArrayList<Node> nList = new ArrayList<>();
                nList.add(temp);
                contextMap.put(var, nList);
                permute(context, k + 1, result);
            }

        }
    }

    @Override
    public ArrayList<Node> visitFLWR(XQueryParser.FLWRContext context) {
        ArrayList<Node> result = new ArrayList<>();
        HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
        if(! DoReWrite)
            permute(context, 0, result);
        else{
            String re = rewrite_Xq(context);
            if (re  == ""){
                permute(context, 0, result);
            }
            else
                result = XQuery.evalRewrited(re);
        }
        contextMap = contextMapOld;
        return result;
    }

    private String rewrite_Xq(XQueryParser.FLWRContext ctx){
        String outXq = "";
        int element_for = ctx.forClause().var().size();
        Map<Integer, Set<String>> division = new HashMap<>();
        int numOfD = 0;
        for(int i=0; i < element_for;i++) {
            String va = ctx.forClause().var(i).getText();
            String pa = ctx.forClause().xq(i).getText().split("/")[0];
            int size = division.size();
            boolean inSet = false;
            // construct the classification
            for(int j = 0; j < size; j++) {
                Set<String> thisSet = division.get(j);
                if(thisSet.contains(pa)) {
                    thisSet.add(va);
                    inSet = true;
                    break;
                }
            }
            if(!inSet) {
                Set<String> set = new HashSet<String>();
                set.add(va);
                division.put(numOfD++,set);
            }
        }

        //where clause
        String[] temp = ctx.whereClause().cond().getText().split("and");
        // each condition:
        String[][] cond = new String[temp.length][2];
        for(int i = 0; i < temp.length;i++) {
            cond[i][0] = temp[i].split("eq|=")[0];
            cond[i][1] = temp[i].split("eq|=")[1];
        }
        if(division.size() == 1) {
            System.out.println("can't join");
            return "";
        }

        int[][] whichD = new int[cond.length][2];

        for(int i=0; i < cond.length; i++) {
            whichD[i][0] = -1;
            whichD[i][1] = -1;
            for(int j = 0; j < division.size();j++) {
                if(division.get(j).contains(cond[i][0])) 
                    whichD[i][0] = j;
                if(division.get(j).contains(cond[i][1])) 
                    whichD[i][1] = j;
            }
        }


        int size = division.size();
        //print out
        outXq += "for $tuple in";
        for (int i = 1; i < size; i++)
                outXq += " join (";
        outXq = printJoin(division, ctx, outXq,cond,whichD);

        String retClause = ctx.returnClause().xq().getText();
        String[] tempRet = retClause.split("\\$");
        for (int i = 0; i < tempRet.length - 1; i++)
            tempRet[i] = tempRet[i]+"$tuple/";
        retClause  = tempRet[0];
        for (int i = 1; i < tempRet.length; i++) {
            String[] cur1 = tempRet[i].split(",",2);
            String[] cur2 = tempRet[i].split("}",2);
            String[] cur3 = tempRet[i].split("/",2);
            String[] cur = cur1;
            if(cur2[0].length() < cur[0].length()) 
                cur = cur2;
            if(cur3[0].length() < cur[0].length())
                cur = cur3;
            tempRet[i] = cur[0] + "/*";
            if(cur == cur1) {
                tempRet[i] += ",";
            }else if(cur == cur2) {
                tempRet[i] += "}";
            }else {
                tempRet[i] += "/";
            }
            tempRet[i] += cur[1];
            retClause = retClause + tempRet[i];
        }
        outXq += "return\n";
        outXq += retClause+"\n";
        writer w = new writer();
        w.writing("rewrite.txt",outXq);
        return outXq;
    }
    private String printJoin(Map<Integer, Set<String>> division, XQueryParser.FLWRContext ctx, String outXq,String[][] cond,int[][] whichD) 
    {
        int element_for = ctx.forClause().var().size();
        for(int p = 0; p < division.size(); p++) {
            Set<String> thisSet = division.get(p);
            String tuples = "";
            int count = 0;
            //print for
            for(int k = 0; k < element_for; k++) {
                String va = ctx.forClause().var(k).getText();
                if(thisSet.contains(va)){
                    if(count == 0) {
                        outXq += "for " + va + " in " + ctx.forClause().xq(k).getText();
                        count++;
                    }
                    else {
                        outXq += ",\n";
                        outXq += "                   " + va + " in " + ctx.forClause().xq(k).getText();
                    }

                    if(tuples.equals(""))
                        tuples = tuples + " <" + va.substring(1) + "> " + " {" + va + "} " + " </" + va.substring(1) + ">";
                    else 
                        tuples = tuples + ", <" + va.substring(1) + "> " + " {" + va + "} " + " </" + va.substring(1) + ">";
                
                }
            }
            outXq += "\n";

            //print where
            for(int j = 0;j < cond.length;j++) {
                int count1 = 0;
                if(thisSet.contains(cond[j][0]) && whichD[j][1] == -1 ) {
                    if(count1 == 0){
                        count1++;
                        outXq += "where " + cond[j][0] + " eq " + cond[j][1] +"\n";
                    }else {
                        outXq += " and  " + cond[j][0] + " eq " + cond[j][1] + "\n";
                    }
                }
            }
            //print return
            tuples = "<tuple> "+tuples+" </tuple>,";
            outXq += "                  return" + tuples + "\n";

            if(p == 0)
                continue;

            //return
            ArrayList<String> left = new ArrayList<String>();
            ArrayList<String> right = new ArrayList<String>();
            for(int i = 0; i < cond.length; i++) 
            {
                if (whichD[i][1] != -1 &&  whichD[i][0] == p && whichD[i][0] > whichD[i][1]) 
                {
                    left.add(cond[i][1].substring(1));
                    right.add(cond[i][0].substring(1));
                }
                else if(whichD[i][0] != -1 &&  whichD[i][1] == p && whichD[i][0]<whichD[i][1]) 
                {
                    left.add(cond[i][0].substring(1));
                    right.add(cond[i][1].substring(1));
                }
            }
            //outXq = PrintJoinCond(ret0,ret1,outXq);
            outXq += "                 [";
            for(int i = 0; i < left.size();i++) {
                outXq +=left.get(i);
                if(i != left.size()-1) {
                    outXq +=",";
                }
            }
            outXq +="], [";
            for(int i = 0; i < right.size();i++) {
                outXq +=right.get(i);
                if(i != right.size()-1)
                    outXq +=",";
            }
            outXq += "]  ";
            outXq += ")\n";
        }
        return outXq;
    }

    @Override
    public ArrayList<Node> visitXqTwo(XQueryParser.XqTwoContext context) {
        ArrayList<Node> result = visit(context.xq(0));
        result.addAll(visit(context.xq(1)));
        return result;
    }

    @Override
    public ArrayList<Node> visitVariable(XQueryParser.VariableContext context) {
        // System.out.println("context.getText(): " + context.getText());
        return contextMap.get(context.getText());
    }

    @Override
    public ArrayList<Node> visitXqRpall(XQueryParser.XqRpallContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        ArrayList<Node> temp = visit(context.xq());
        result.addAll(temp);
        ll.addAll(temp);
        while(!ll.isEmpty()){
            Node tempNode = ll.poll();
            result.addAll(children(tempNode));
            ll.addAll(children(tempNode));
        }
        current_list = result;
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitXqP(XQueryParser.XqPContext context) {
        return visit(context.xq());
    }

    @Override
    public ArrayList<Node> visitSC(XQueryParser.SCContext context) {
        Node temp = createText(context.StringConstant().getText().substring(1, context.StringConstant().getText().length()-1));
        ArrayList<Node> result = new ArrayList<>();
        result.add(temp);
        return result;
    }

    @Override
    public ArrayList<Node> visitXqLet(XQueryParser.XqLetContext context) {
        HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
        ArrayList<Node> result = visitChildren(context);
        contextMap = contextMapOld;
        return result;
    }

    @Override
    public ArrayList<Node> visitXqRp(XQueryParser.XqRpContext context) {
        current_list = visit(context.xq());
        return visit(context.rp());
    }

    private ArrayList<Node> getItems (int v, XQueryParser.ForClauseContext context) {
        ArrayList<Node> result = new ArrayList<>();
        ArrayList<Node> tempList = visit(context.xq(v));
        if(context.xq().size() == 1) {
            for(Node temp: tempList) {
                ArrayList<Node> tempList2 = new ArrayList<>();
                tempList2.add(temp);
                contextMap.put(context.var(v).getText(), tempList2);
                result.add(temp);
            } 
            return result;
        }
        else {
            for(Node temp: tempList) {
                HashMap<String, ArrayList<Node>> contextMapOld = new HashMap<>(contextMap);
                ArrayList<Node> tempList2 = new ArrayList<>();
                tempList2.add(temp);
                contextMap.put(context.var(v).getText(), tempList2);
                result.addAll(getItems(v + 1, context));
                contextMap = contextMapOld;
            }
            return result;
        }
    }
    @Override 
    public ArrayList<Node> visitJoinClause(XQueryParser.JoinClauseContext ctx)
         {
            ArrayList<Node> left = visit(ctx.xq(0));
            ArrayList<Node> right = visit(ctx.xq(1));
            int Size = ctx.idList(0).NAME().size();
            String [] leftlist = new String [Size];
            String [] rightlist  = new String [Size];
            for (int i = 0; i < Size; i++)
            {
                leftlist[i] = ctx.idList(0).NAME(i).getText();
                rightlist[i] = ctx.idList(1).NAME(i).getText();
            }
            HashMap<String,  ArrayList<Node>> hashMapLeft= new HashMap<>();
            ArrayList<Node> joinresult = new ArrayList<>();
            for (Node tuple_left: left)
            {
                ArrayList<Node> tuple_children_left = children(tuple_left);
                String left_key = "";
                for (String leftid: leftlist) 
                {
                    for (Node tuple_child: tuple_children_left)
                    {
                        if (leftid.equals(tuple_child.getNodeName()))
                        {
                          left_key += tuple_child.getFirstChild().getTextContent();
                        }
                    }
                }
                if(!hashMapLeft.containsKey(left_key))
                    hashMapLeft.put(left_key,new ArrayList<Node>());
                hashMapLeft.get(left_key).add(tuple_left);
            }

            for (Node tuple_right: right)
            {
                ArrayList<Node> tuple_children_right = children(tuple_right);
                String right_key = "";
                for (String rightid: rightlist) 
                {
                    for (Node tuple_child: tuple_children_right)
                    {
                        if (rightid.equals(tuple_child.getNodeName())) 
                        {
                            right_key += tuple_child.getFirstChild().getTextContent();
                        }
                    }
                }
                if (hashMapLeft.containsKey(right_key))
                {    
                    ArrayList<Node> result = new ArrayList<>();
                    for (Node left_list_node: left)
                    {          
                    ArrayList<Node> left_list_node_Children = children(left_list_node);
                    left_list_node_Children.addAll(children(tuple_right));
                    result.add(createNode("tuple", left_list_node_Children));
                    }
                    joinresult.addAll(result);
                }
            }
            return joinresult;
       }

    // @Override 
    // public ArrayList<Node> visitJoinClause(XQueryParser.JoinClauseContext ctx){
    //     ArrayList<Node> left = visit(ctx.xq(0));
    //     ArrayList<Node> right = visit(ctx.xq(1));
    //     int idSize = ctx.idList(0).NAME().size();
    //     String [] idListLeft = new String [idSize];
    //     String [] idListRight = new String [idSize];
    //     for (int i = 0; i < idSize; i++){
    //         idListLeft[i] = ctx.idList(0).NAME(i).getText();
    //         idListRight[i] = ctx.idList(1).NAME(i).getText();
    //     }
    //     HashMap<String, ArrayList<Node>> hashMapOnLeft = buildHashTable(left, idListLeft);
    //     ArrayList<Node> result = probeJoin(hashMapOnLeft, right, idListLeft, idListRight);

    //     return result;

    // }

    private ArrayList<Node> probeJoin(HashMap<String, ArrayList<Node>> hashMapOnLeft, ArrayList<Node> right, String [] idListLeft, String []idListRight){
        ArrayList<Node> result = new ArrayList<>();
        for (Node tuple: right){
            ArrayList<Node> children = children(tuple);
            String key = "";
            for (String hashAtt: idListRight) {
                for (Node child: children){
                    if (hashAtt.equals(child.getNodeName())) {
                        key += child.getFirstChild().getTextContent();
                    }
                }
            }

            if (hashMapOnLeft.containsKey(key))
                result.addAll(product(hashMapOnLeft.get(key),tuple));
        }
        return result;
    }
    private ArrayList<Node> product(ArrayList<Node> leftList, Node right){
        ArrayList<Node> result = new ArrayList<>();
        for (Node left: leftList){
            ArrayList<Node> newTupleChildren = children(left);
            newTupleChildren.addAll(children(right));
            result.add(createNode("tuple", newTupleChildren));
        }
        return result;
    }


    private HashMap buildHashTable(ArrayList<Node> tupleList, String [] hashAtts){
        HashMap<String, ArrayList<Node>> result = new HashMap<>();
        for (Node tuple: tupleList){
            ArrayList<Node> children = children(tuple);
            String key = "";
            for (String hashAtt: hashAtts) {
                for (Node child: children){
                    if (hashAtt.equals(child.getNodeName()))
                        key += child.getFirstChild().getTextContent();
                }
            }
            if (result.containsKey(key))
                result.get(key).add(tuple);
            else{
                ArrayList<Node> value = new ArrayList<>();
                value.add(tuple);
                result.put(key, value);
            }
        }
        return result;
    }



    @Override 
    public ArrayList<Node> visitXQJoin(XQueryParser.XQJoinContext context){
        return visit(context.joinClause());
    }

    @Override 
    public ArrayList<Node> visitForClause(XQueryParser.ForClauseContext context) { 
        ArrayList<Node> result = new ArrayList<>();
        result.addAll(getItems(0, context));
        return result;
    }
    
    @Override
    public ArrayList<Node> visitLetClause(XQueryParser.LetClauseContext context) {
        for (int i = 0; i < context.var().size(); i++) {
            contextMap.put(context.var(i).getText(), visit(context.xq(i)));
        }
        return null;
    }

    @Override
    public ArrayList<Node> visitWhereClause(XQueryParser.WhereClauseContext context) {
        return visit(context.cond());
    }

    @Override 
    public ArrayList<Node> visitReturnClause(XQueryParser.ReturnClauseContext context) { 
        return visit(context.xq());
    }
   
    @Override
    public ArrayList<Node> visitXqEqual(XQueryParser.XqEqualContext context) {
        ArrayList<Node> tempList = current_list;
        ArrayList<Node> left = visit(context.xq(0));
        current_list = tempList;
        ArrayList<Node> right = visit(context.xq(1));
        current_list = tempList;
        ArrayList<Node> result = new ArrayList<>();
        for (Node i : left) {
            for (Node j : right) {
                if (i.isEqualNode(j)) {
                    result.add(i);
                    return result;
                }
            }
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqEmpty(XQueryParser.XqEmptyContext context) {
        ArrayList<Node> xqResult = visit(context.xq());
        ArrayList<Node> result = new ArrayList<>();
        if (xqResult.isEmpty()){
            Node dummy = inputDoc.createElement("dummy");
            result.add(dummy);
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondOr(XQueryParser.XqCondOrContext context) {
        ArrayList<Node> left = visit(context.cond(0));
        if (!left.isEmpty()){
            return left;
        }
        ArrayList<Node> right = visit(context.cond(1));
        if (!right.isEmpty()){
            return right;
        }
        return new ArrayList<>();
    }

    // @Override
    // public ArrayList<Node> visitXqSome(XQueryParser.XqSomeContext context) {
    //     for (int i = 0; i < context.var().size(); i++) {
    //         contextMap.put(context.var(i).getText(), visit(context.xq(i)));
    //     }
    //     return visit(context.cond());
    // }
    @Override
    public ArrayList<Node> visitXqSome(XQueryParser.XqSomeContext ctx) {
        HashMap<String, ArrayList<Node>> copyContext = new HashMap<>(contextMap);
        ArrayList<Node> nodes = current_list;
        int numVars = ctx.var().size();
        for (int i = 0; i < numVars; ++i) {
            String varName = ctx.var(i).getText();
            ArrayList<Node> xquery = visit(ctx.xq(i));
            contextMap.put(varName, xquery);
        }
        ArrayList<Node> cond = visit(ctx.cond());
        contextMap = copyContext;
        current_list = nodes;
        return cond;
    }


    @Override
    public ArrayList<Node> visitXqIs(XQueryParser.XqIsContext context) {
        ArrayList<Node> tempList = current_list;
        ArrayList<Node> left = visit(context.xq(0));
        current_list = tempList;
        ArrayList<Node> right = visit(context.xq(1));
        current_list = tempList;
        ArrayList<Node> result = new ArrayList<>();
        for (Node i : left) {
            for (Node j : right) {
                if (i == j) {
                    result.add(i);
                    return result;
                }
            }
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondNot(XQueryParser.XqCondNotContext context) {
        ArrayList<Node> notList = visit(context.cond());
        ArrayList<Node> result = new ArrayList<>();
        if (notList.isEmpty()){
            Node dummy = inputDoc.createElement("dummy");
            result.add(dummy);
        }
        return result;
    }

    @Override
    public ArrayList<Node> visitXqCondP(XQueryParser.XqCondPContext context) {
        return visit(context.cond());
    }

    @Override
    public ArrayList<Node> visitXqCondAnd(XQueryParser.XqCondAndContext context) {
        ArrayList<Node> left = visit(context.cond(0));
        ArrayList<Node> right = visit(context.cond(1));
        if (!left.isEmpty() && !right.isEmpty()){
            return left;
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitApChildren(XQueryParser.ApChildrenContext context) {
        return visitChildren(context);
    }

    @Override
    public ArrayList<Node> visitApAll(XQueryParser.ApAllContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        visit(context.doc());
        result.addAll(current_list);
        ll.addAll(current_list);
        while(!ll.isEmpty()) {
            Node temp = ll.poll();
            result.addAll(children(temp));
            ll.addAll(children(temp));
        }
        current_list = result;
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitDoc(XQueryParser.DocContext context) {
        if(inputDoc == null) {
            File xmlFile = new File(context.fname().getText());
            DocumentBuilderFactory docBF = DocumentBuilderFactory.newInstance();
            docBF.setIgnoringElementContentWhitespace(true);
            DocumentBuilder docB = null;
            try {
                docB = docBF.newDocumentBuilder();
            } catch (ParserConfigurationException pE1) {
                pE1.printStackTrace();
            }

            try {
                if (docB != null) {
                    inputDoc = docB.parse(xmlFile);
                }
            } catch(Exception e) {
                e.printStackTrace();
            }

            inputDoc.getDocumentElement().normalize();
        }
        ArrayList<Node> result = new ArrayList<>();
        result.add(inputDoc);
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpAllChildren(XQueryParser.RpAllChildrenContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for(Node temp : current_list) {
            result.addAll(children(temp));
        }
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpP(XQueryParser.RpPContext context) {
        return visit(context.rp());
    }

    @Override
    public ArrayList<Node> visitTagName(XQueryParser.TagNameContext context) {
        ArrayList<Node> result = new ArrayList<>();
        String tName = context.getText();
        for(Node temp : current_list) {
            ArrayList<Node> list = children(temp);
            for(Node i : list) {
                if(i.getNodeName().equals(tName)) result.add(i);
            }
        }
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpAll(XQueryParser.RpAllContext context) {
        ArrayList<Node> result = new ArrayList<>();
        LinkedList<Node> ll = new LinkedList<>();
        visit(context.rp(0));
        result.addAll(current_list);
        ll.addAll(current_list);
        while(!ll.isEmpty()) {
            Node temp = ll.poll();
            result.addAll(children(temp));
            ll.addAll(children(temp));
        }
        current_list = result;
        return visit(context.rp(1));
    }

    @Override
    public ArrayList<Node> visitRpParent(XQueryParser.RpParentContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for(Node temp : current_list) {
            if(!result.contains(temp.getParentNode())) result.add(temp.getParentNode());
        }
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitAttribute(XQueryParser.AttributeContext context) {
        ArrayList<Node> result = new ArrayList<>();
        hasAttribute = true;
        for (Node temp : current_list) {
            Element e = (Element) temp;
            String attr = e.getAttribute(context.NAME().getText());
            if (!attr.equals("")) {
                result.add(temp);
                attr = context.NAME().getText()+"=\""+ attr +"\"";
            }
        }
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpChildren(XQueryParser.RpChildrenContext context) {
        visit(context.rp(0));
        ArrayList<Node> result = visit(context.rp(1));
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpText(XQueryParser.RpTextContext context) {
        ArrayList<Node> result = new ArrayList<>();
        for (Node n : current_list) {
            ArrayList<Node> list = children(n);
            for (Node child : list) {
                if (child.getNodeType() == Node.TEXT_NODE){
                    result.add(child);
                }
            }
        }
        current_list = result;
        return result;
    }

    @Override
    public ArrayList<Node> visitRpCurrent(XQueryParser.RpCurrentContext context) {
        return current_list;
    }

    @Override
    public ArrayList<Node> visitRpTwo(XQueryParser.RpTwoContext context) {
        ArrayList<Node> result1 = new ArrayList<>();
        ArrayList<Node> result2 = new ArrayList<>();
        ArrayList<Node> tempList = new ArrayList<>(current_list);
        result1.addAll(visit(context.rp(0)));
        current_list = tempList;
        result2.addAll(visit(context.rp(1)));
        result1.addAll(result2);

        current_list = result1;
        return result1;
    }

    @Override
    public ArrayList<Node> visitRpFilter(XQueryParser.RpFilterContext context) {
        ArrayList<Node> result = visit(context.rp());
        ArrayList<Node> filter= visit(context.filter());
        if (hasAttribute) {
            current_list = filter;
            hasAttribute = false;
            return filter;
        }
        else if (filter.isEmpty()) {
            return new ArrayList<>();
        }
        else return result;
    }

    @Override
    public ArrayList<Node> visitFitAnd(XQueryParser.FitAndContext context) {
        ArrayList<Node> left = visit(context.filter(0));
        ArrayList<Node> right = visit(context.filter(1));
        if (!left.isEmpty() && !right.isEmpty()) {
            return left;
        }
        else return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFitEqual(XQueryParser.FitEqualContext context) {
        ArrayList<Node> tempList = current_list;
        ArrayList<Node> left = visit(context.rp(0));
        current_list = tempList;
        ArrayList<Node> right = visit(context.rp(1));
        current_list = tempList;
        for (Node i : left) {
            for (Node j : right) {
                if (i.isEqualNode(j)) {
                    return tempList;
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFitNot(XQueryParser.FitNotContext context) {
        ArrayList<Node> result = visit(context.filter());
        if (!result.isEmpty()) {
            return current_list;
        }
        else return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFitOr(XQueryParser.FitOrContext context) {
        ArrayList<Node> left = visit(context.filter(0));
        ArrayList<Node> right = visit(context.filter(1));
        if (!left.isEmpty() || !right.isEmpty()) {
            return current_list;
        }
        else return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFitIs(XQueryParser.FitIsContext context) {
        ArrayList<Node> tempList = current_list;
        ArrayList<Node> left = visit(context.rp(0));
        current_list = tempList;
        ArrayList<Node> right = visit(context.rp(1));
        current_list = tempList;
        for (Node i : left) {
            for (Node j : right) {
                if (i == j) {
                    return tempList;
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public ArrayList<Node> visitFitP(XQueryParser.FitPContext context) {
        return visit(context.filter());
    }

    @Override
    public ArrayList<Node> visitFitRp(XQueryParser.FitRpContext context) {
        ArrayList<Node> tempList = current_list;
        ArrayList<Node> result = visit(context.rp());
        current_list = tempList;
        return result;
    }

    private Node createNode(String s, ArrayList<Node> nodeList) {
        Node result = outputDoc.createElement(s);
        for (Node temp : nodeList) {
            if (temp != null) {
                Node newNode = outputDoc.importNode(temp, true);
                result.appendChild(newNode);
            }
        }
        return result;
    }

    private Node createText(String s) {
        return inputDoc.createTextNode(s);
    }

    private static ArrayList<Node> children(Node n){
        ArrayList<Node> childrenList = new ArrayList<>();
        for(int i = 0; i < n.getChildNodes().getLength(); ++i){
            childrenList.add(n.getChildNodes().item(i));
        }
        return childrenList;
    }

    public static ArrayList<Node> getChildren(ArrayList<Node> n){
        /**
         * return the children of the a node (just the next level)
         */
        ArrayList<Node> childrenList = new ArrayList<Node>();
        for(int j = 0; j < n.size(); j++) {
            Node curNode = n.get(j);
            for (int i = 0; i < curNode.getChildNodes().getLength(); i++) {
                childrenList.add(curNode.getChildNodes().item(i));
            }
        }
        return childrenList;
    }





}
