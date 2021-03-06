/*************************************************************************
    > File Name: Main.java 
    > Created Time: 2018-02-20 21:54
 ************************************************************************/
import java.io.*;
import java.util.*;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.OutputKeys;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

public class XQueryMain {
    public static void main(String[] args) throws IOException{
        String queryFile = "XQueryTest.txt";
        String resultPath = "./output.xml";
        // ANTLRInputStream input = null;
        // try {
        //     InputStream is = new FileInputStream(queryFile);
        //     input = new ANTLRInputStream(is);
        // } catch (Exception e1) {
        //     e1.printStackTrace();
        // }

        CharStream input = CharStreams.fromFileName(queryFile);
        XQueryParser parser = new XQueryParser(new CommonTokenStream(new XQueryLexer(input)));
        ParseTree tree = parser.xq();
        XQueryExtendsVisitor evaluation = new XQueryExtendsVisitor();
        ArrayList<Node> finalResult = evaluation.visit(tree);
        createResultFile(evaluation.output, finalResult, resultPath);

        System.out.println("Saving output.xml");
        if (!finalResult.isEmpty()){
            System.out.println("finalResult size: " + finalResult.size());
            for (Node n : finalResult) {
                System.out.println("Node name: " + n.getNodeName());
                if (n.getChildNodes() != null) {
                    for (int i = 0; i < n.getChildNodes().getLength(); i ++) {
                        System.out.println(n.getChildNodes().item(i).getTextContent());
                    }

                }
            }
        }
    }

    public static void createResultFile(Document doc, ArrayList<Node> finalResult, String resultPath) {
        doc.appendChild(finalResult.get(0));
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(doc);
            StreamResult res = new StreamResult(resultPath);
            transformer.transform(source, res);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
