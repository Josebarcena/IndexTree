package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.queryparser.classic.ParseException;
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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

    public static void main(String[] args) throws IOException, ParseException {
        String usage = "java org.apache.lucene.demo.IndexTrecCovid"
                + " [-search jm <lambda> | bm25 <k1>] [-index <ruta>] [-cut <n>]  [-top <m>] [-queries all | <int1> | <int1-int2>]\n\n";
        String queriesFile = "src/files/queries.jsonl";
        String search = null;
        String queries = null;
        float lambda = 0;
        int numQueries = 0;
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
            Directory dir = FSDirectory.open(Path.of(indexPath));

            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
            String sJm = null;
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);

            switch (search) {
                case "jm": {
                    LMJelinekMercerSimilarity jm = new LMJelinekMercerSimilarity(lambda);
                    searcher.setSimilarity(jm);
                    sJm = "lambda." + lambda;
                    break;
                }
                case "bm25": {
                    BM25Similarity bm25 = new BM25Similarity(lambda, 0.75f);
                    searcher.setSimilarity(bm25);
                    sJm = "k1." + lambda;
                    break;
                }
                default: {
                    throw new IllegalArgumentException("search not valid on creating: " + search);
                }
            }


            QueryParser parser = new QueryParser("text", analyzer);
            String[] subQueries = new String[50];

            //Definir limites
            int init;
            int last;
            if(Objects.requireNonNull(queries).equals("all")){
                init = 1;
                last = subQueries.length + 1;
                numQueries = subQueries.length + 1;
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
                String query = subQueries[i - 1].toLowerCase();
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

                //System.out.println(searcher.getSimilarity());
                TopDocs topDocs = searcher.search(parser.parse(query), Math.max(top, cut));
                System.out.println("TOP DOCS PARA LA QUERY " + i);
                bufTxt.write("TOP DOCS PARA LA QUERY " + i + "\n");
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    System.out.println("DOC:" + topDocs.scoreDocs[j].doc + ":");
                    bufTxt.write("DOC:" + topDocs.scoreDocs[j].doc + ":" + "\n");

                    //Score
                    System.out.println("\tscore: " + topDocs.scoreDocs[j].score);
                    bufTxt.write("\tscore: " + topDocs.scoreDocs[j].score + "\n");
                    Document doc = reader.document(topDocs.scoreDocs[j].doc);
                    String id = doc.get("id");

                    //RELEVANTE/NO RELEVANTE
                    if (relevantDocsID.contains(id)) {
                        System.out.println("\tRELEVANTE");
                        bufTxt.write("\tRELEVANTE" + "\n");
                    }
                    else if(partialRelevantDocsID.contains(id)){
                        System.out.println("\tPARCIALMENTE RELEVANTE");
                        bufTxt.write("\tPARCIALMENTE RELEVANTE" + "\n");
                    }
                    else{
                        System.out.println("\tNO RELEVANTE");
                        bufTxt.write("\tNO RELEVANTE" + "\n");
                    }

                    //Campos
                    System.out.println("Campos indexados:");
                    bufTxt.write("Campos indexados:" + "\n");
                    List<IndexableField> campos = reader.document(topDocs.scoreDocs[j].doc).getFields();
                    for (IndexableField campo : campos) {
                        System.out.println("\t" + campo.name() + " = " + campo.stringValue());
                        bufTxt.write("\t" + campo.name() + " = " + campo.stringValue() + "\n");
                    }
                }
                float relevantesA = 0;
                float suMetr = 0;
                double rr = 0;
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    Document doc = reader.document(topDocs.scoreDocs[j].doc);
                    String id = doc.get("id");
                    if (relevantDocsID.contains(id) || partialRelevantDocsID.contains(id)) {
                        relevantesA++;
                        suMetr += relevantesA / (j + 1);
                        if (rr == 0) {
                            rr = 1.0 / (j + 1);
                        }
                    }
                }
                float precision = relevantesA/topDocs.scoreDocs.length;
                float recall = relevantesA / relevantDocsID.size();
                float ap = suMetr / relevantDocsID.size();

                //Escribimos las metricas individuales de cada una (txt/csv/pantalla)
                bufCsv.write(i+";"+String.format("%.10f",precision)+";"+String.format("%.10f",recall)+";"+String.format("%.10f",ap)+"\n");

                acumP += precision;
                acumRec += recall;

                System.out.println("\nMétricas para la query: " + i);
                bufTxt.write("\nMétricas para la query: " + i + "\n");

                System.out.println("\tP@n: " + String.format("%.10f",precision));
                bufTxt.write("\tP@n: " + String.format("%.10f",precision) + "\n");

                System.out.println("\tRecall@n:" +String.format("%.10f",recall));
                bufTxt.write("\tRecall@n:" + String.format("%.10f",recall) + "\n");

                System.out.println("\tAP@n: " + String.format("%.10f",ap));
                bufTxt.write("\tAP@n: " + String.format("%.10f",ap) + "\n");

                if (!Float.isInfinite(ap))
                    acumMap += ap;

                System.out.println("\tRR: " + String.format("%.10f",rr));
                bufTxt.write("\tRR: " + String.format("%.10f",rr) + "\n");
                acumRR += rr;

                System.out.println("-------------------------------------------------------------------------------\n");
                bufTxt.write("-------------------------------------------------------------------------------" + "\n");
            }

            // Calculamos las métricas promediadas
            float pPromedio = acumP / numQueries;
            float recPromedio = acumRec / numQueries;
            float map = acumMap / numQueries;
            float mrr = acumRR / numQueries;

            //Las imprimimos y escribimos
            System.out.println("P@n promedio: " + String.format("%.10f",pPromedio));
            bufTxt.write("P@n promedio: " + String.format("%.10f",pPromedio) + "\n");

            System.out.println("Recall@n promedio: " + String.format("%.10f",recPromedio));
            bufTxt.write("Recall@n promedio: " + String.format("%.10f",recPromedio) + "\n");

            System.out.println("MAP@n: " + String.format("%.10f",map));
            bufTxt.write("MAP@n: " + String.format("%.10f",map) + "\n");

            System.out.println("MRR: " + String.format("%.10f",mrr));
            bufTxt.write("MRR: " + String.format("%.10f",mrr) + "\n");

            bufCsv.write("\nPromedios:;P@n;Recall@n;MAP@n;MRR\n");
            bufCsv.write(";"+String.format("%.10f",pPromedio)+";"+String.format("%.10f",recPromedio)+";"+String.format("%.10f",map)+";"+String.format("%.10f",mrr));

            //Cerramos buffers
            bufCsv.close();
            bufTxt.close();
            writerCsv.close();
            bufTxt.close();

        }catch (IOException e){
            throw new IOException(e);
        }


    }

}