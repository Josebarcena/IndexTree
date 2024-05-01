package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class SearchEvalTrecCovid {
    public static float acumP = 0;
    public static float acumRec = 0;
    public static float acumMap = 0;
    public static float acumRR= 0;
    public static float sumP = 0;
    public static float sumR = 0;
    public static float sumM = 0;
    public static int top = 0;
    public static int cut = 0;
    public static String indexPath;

    public static void main(String[] args) throws IOException {
        String usage = "java org.apache.lucene.demo.IndexTrecCovid"
                + " [-search jm <lambda> | bm25 <k1>] [-index <ruta>] [-cut <n>]  [-top <m>] [-queries all | <int1> | <int1-int2>]\n\n";
        String queriesFile = "src/files/queries.jsonl";
        String search = null;
        String queries = null;
        float lambda = 0;
        int numQueries = 0;
        System.out.println(Arrays.toString(args));
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {

                case "-index":
                    indexPath = args[++i];
                    if (indexPath == null) {
                        throw new IllegalArgumentException("unknow index: " + args[i] + "\n" + usage);
                    }
                    break;

                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    if (cut == 0) {
                        throw new IllegalArgumentException("unknow cut: " + args[i] + "\n" + usage);
                    }
                    break;
                case "-top":
                    top = Integer.parseInt(args[++i]);
                    if (top == 0) {
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
                    if (search == null) {
                        throw new IllegalArgumentException("search not valid: " + args[i] + "\n" + usage);
                    } else if (search.equals("jm") || search.equals("bm25")) {
                        lambda = Float.parseFloat(args[++i]);
                        if (lambda == 0) {
                            throw new IllegalArgumentException("lambda not valid: " + args[i] + "\n" + usage);
                        }
                    } else {
                        throw new IllegalArgumentException("unknow search: " + args[i] + "\n" + usage);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }
        //Comprobamos que esta bien
        if (indexPath == null || queries == null || search == null) {
            System.err.println("Usage: " + usage);
            System.exit(-2);
        }

        //Definir Similarity
        try {
            System.out.println(indexPath);
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
            String[] subQueries = new String[50];

            //Definir limites
            int init;
            int last;
            if(Objects.requireNonNull(queries).equals("all")){
                init = 1;
                last = queries.length();
                numQueries = queries.length();
            }else if(queries.contains("-")){
                String[] limit = queries.split("-");
                init = Integer.parseInt(limit[0]);
                last = Integer.parseInt(limit[1]) + 1;
                numQueries = last-init+1;
            }else{
                init = Integer.parseInt(queries);
                last = init + 1;
                numQueries = 1;
            }

            //Parsear query
            ObjectMapper objectMapper = new ObjectMapper();
            BufferedReader br = new BufferedReader(new FileReader(queriesFile));
            String line;
            int x = 0;
            while ((line = br.readLine()) != null) {
                // Convertir cada línea a un JsonNode
                JsonNode jsonNode = objectMapper.readTree(line);

                // Extraer los atributos requeridos
                int id = jsonNode.path("_id").asInt();
                if( id >= init && id < last ) {
                    subQueries[x] = jsonNode.path("metadata").path("query").asText();
                    x++;
                }
                if(jsonNode.path("_id").asInt() == last - 1){
                    break;
                }
            }


            //Analizamos las queries
            ArrayList<String> relevantDocsID = new ArrayList<>();
            ArrayList<String> partialRelevantDocsID = new ArrayList<>();

            File out = new File("TREC-COVID." + search + "." + cut + ".hits.lambda." + sJm + ".q" + queries + ".txt");
            FileWriter writerTxt = new FileWriter(out);
            BufferedWriter bufTxt = new BufferedWriter(writerTxt);

            File csv = new File("TREC-COVID." + search + "." + cut + ".hits.lambda." + sJm + ".q" + queries + ".csv");
            FileWriter writerCsv = new FileWriter(csv);
            BufferedWriter bufCsv = new BufferedWriter(writerCsv);

            bufCsv.write("queryID, P@n" + cut + ", RECALL@" + cut + ", APN@" + cut + "\n");


            for(int i = init; i< last; i++) {
                BufferedReader bufferedReader2 = new BufferedReader(new FileReader("src/files/qrels/test.tsv"));
                String query = subQueries[i - 1];//.toLowerCase();
                //Docs relevantes de cada query
                String lineDoc = bufferedReader2.readLine();
                while ((lineDoc = bufferedReader2.readLine()) != null) {
                    String[] parts = lineDoc.split("\t"); // Divide la línea en partes separadas por tabulaciones
                    int queryId = Integer.parseInt(parts[0]); // Obtiene el id de la consulta
                    String corpusId = parts[1]; // Obtiene el id del corpus
                    int score = Integer.parseInt(parts[2]); // Obtiene el scoreç
                    if (queryId == i && score == 2) {
                        relevantDocsID.add(corpusId);
                    } else if (queryId == i && score == 1) {
                        partialRelevantDocsID.add(corpusId);
                    }

                }

            }

        }catch (IOException e){
            throw new IOException(e);
        }


    }

}
