package org.example;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;

public class SearchEvalTrecCovid {
    public static String usage = "java org.apache.lucene.demo.IndexTrecCovid"
            + " [-search jm <lambda> | bm25 <k1>] [-index <ruta>] [-cut <n>]  [-top <m>] [-queries all | <int1> | <int1-int2>]\n\n";

    public static String indexPath;
    public static int cut, top;

    public static void main(String[] args) throws IOException {
        String search = null;
        String queries = null;
        float lambda = 0;
        System.out.println(Arrays.toString(args));
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-index":
                    indexPath = args[++i];
                    if(indexPath == null){
                        throw new IllegalArgumentException("unknow index: " + args[i] + "\n" + usage);
                    }
                    break;

                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    if(cut == 0){
                        throw new IllegalArgumentException("unknow cut: " + args[i] + "\n" + usage);
                    }
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    if(top == 0){
                        throw new IllegalArgumentException("unknow top: " + args[i] + "\n" + usage);
                    }
                    break;
                case "-queries": {
                    queries = args[++i];//comprobar si es: all, x, x-y
                    if (queries == null) {
                        throw new IllegalArgumentException("unknow queries: " + args[i] + "\n" + usage);
                    }
                    break;
                }
                case "-search":
                    search = args[++i];
                    if(search == null){
                        throw new IllegalArgumentException("search not valid: " + args[i] + "\n" + usage);
                    }
                    else if(search.equals("jm") || search.equals("bm25")){
                        lambda = Float.parseFloat(args[++i]);
                        if(lambda == 0){
                            throw new IllegalArgumentException("lambda not valid: " + args[i] + "\n" + usage);
                        }
                    }
                    else{
                        throw new IllegalArgumentException("unknow search: " + args[i] + "\n" + usage);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
            try {
                Directory dir = FSDirectory.open(Paths.get(indexPath));
                Analyzer analyzer = new StandardAnalyzer();
                IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
                String sJm = null;
                LMJelinekMercerSimilarity jm = null;
                switch (search) {
                    case "jm": {
                        jm = new LMJelinekMercerSimilarity(lambda);
                        iwc.setSimilarity(jm);
                        sJm = "lambda." + lambda;
                        break;
                    }
                    case "bm25": {
                        BM25Similarity bm25 = new BM25Similarity(lambda, 0.75f);
                        iwc.setSimilarity(bm25);
                        sJm = "k1." + lambda;
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("search not valid on creating: " + search);
                    }
                }

                IndexReader reader = DirectoryReader.open(dir);
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(jm);
                QueryParser parser = new QueryParser("text", analyzer);


            }catch (IOException e){
                throw new IOException(e);
            }
        }

    }

}
