package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriterConfig;
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

public class TrainingTestTrecCovid {
    public static void main(String[] args) throws IOException, ParseException {
        String usage = "[-evaljm <int1-int2> <int3-int4> || -evalbm25 <int1-int2> <int3-int4>] [-cut <n>] [-metrica P | R | MRR | MAP] [-index <ruta>]\n\n";
        String index = null;
        int cut = 0, int1 = 0, int2 = 0, int3 = 0, int4 = 0;
        String method = null;
        String metrica = null;
        String[] parts1;
        String[] parts2;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-evaljm":
                    if(method != null){
                        throw new IllegalArgumentException(usage);
                    }
                    method = "jm";
                    parts1 = args[++i].split("-");
                    if (parts1.length == 2) {
                        int1 = Integer.parseInt(parts1[0]);
                        int2 = Integer.parseInt(parts1[1]);
                    }
                    parts2 = args[++i].split("-");
                    if (parts2.length == 2) {
                        int3 = Integer.parseInt(parts2[0]);
                        int4 = Integer.parseInt(parts2[1]);
                    }
                    break;

                case "-evalbm25":
                    if(method != null){
                        throw new IllegalArgumentException(usage);
                    }
                    method = "bm25";
                    parts1 = args[++i].split("-");
                    if (parts1.length == 2) {
                        int1 = Integer.parseInt(parts1[0]);
                        int2 = Integer.parseInt(parts1[1]);
                    }
                    parts2 = args[++i].split("-");
                    if (parts2.length == 2) {
                        int3 = Integer.parseInt(parts2[0]);
                        int4 = Integer.parseInt(parts2[1]);
                    }
                    break;

                case "-cut":
                    cut = Integer.parseInt(args[++i]);
                    break;
                case "-metrica":
                    metrica = args[++i];
                    break;

                case "-index":
                    index = args[++i];
                    break;

                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        //Init
        ObjectMapper objectMapper = new ObjectMapper();
        Directory dir = FSDirectory.open(Path.of(index));
        Analyzer analyzer = new StandardAnalyzer();
        IndexReader reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);

        //Parseamos las queries
        QueryParser parser = new QueryParser("text", analyzer);
        BufferedReader bufferedReader = new BufferedReader(new FileReader("src/files/queries.jsonl"));
        String line;
        ArrayList<String> subQueries = new ArrayList<>();

        while ((line = bufferedReader.readLine()) != null) {
            // Convertir cada línea a un JsonNode
            JsonNode jsonNode = objectMapper.readTree(line);
            subQueries.add(jsonNode.path("metadata").path("query").asText());
        }

        //Creamos el CSV de TRAIN
        File csv = new File ("TREC-COVID." + method + ".training." + int1 + "-" + int2 + ".test." + int3 + "-" + int4 + "."
                + metrica + cut + ".training.csv");
        FileWriter writerCsv = new FileWriter(csv);
        BufferedWriter bufCsv = new BufferedWriter(writerCsv);


        double[] values;
        if(method.equals("jm")){
            bufCsv.write(metrica + "@" + cut + ";0.001;0.1;0.2;0.3;0.4;0.5;0.6;0.7;0.8;0.9;1.0\n");
            values = new double[]{0.001, 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0};
        }else {
            bufCsv.write(metrica + "@" + cut + ";0.4;0.6;0.8;1.0;1.2;1.4;1.6;1.8;2.0\n");
            values = new double[]{0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
        }

        //Para cada query de train:
        float[] sum = new float[values.length];
        for(int i = int1; i<=int2; i++){
            //escribimos su numero en el csv
            bufCsv.write(i+";");

            //Medimos con los diferentes valores de lambda (k1)
            for(int aux = 0; aux<values.length; aux++){
                double lambda = values[aux];
                if(method.equals("jm")){
                    searcher.setSimilarity(new LMJelinekMercerSimilarity((float) lambda));//double por la precision baja de float
                }else{
                    searcher.setSimilarity(new BM25Similarity((float) lambda, 0.75f));//double por la precision baja de float
                }

                ArrayList<String> trainrelevants = GetRelevants(i);
                String query = subQueries.get(i-1).toLowerCase();
                TopDocs topDocs = searcher.search(parser.parse(query), cut);
                //Calculamos las metricas de cada una
                float relevantesA = 0;
                float suMetr = 0;
                double rr = 0;
                for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                    Document doc = reader.document(topDocs.scoreDocs[j].doc);
                    String id = doc.get("id");

                    if (trainrelevants.contains(id.toLowerCase())) {
                        relevantesA++;
                        suMetr += relevantesA / (j + 1);
                        if (rr == 0) {
                            rr = 1.0 / (j + 1);
                        }
                    }
                }
                float precision = relevantesA/topDocs.scoreDocs.length;
                float recall = relevantesA / trainrelevants.size();
                float ap = suMetr / trainrelevants.size();

                //Imprimimos sobre cada una su metrica con su valor
                if(method.equals("jm"))
                    System.out.print("Query " + i + " Lambda " + String.format("%.1f",lambda));
                else
                    System.out.print("Query " + i + " K1 " + String.format("%.0f",lambda));

                switch(metrica){
                    case "P":
                        bufCsv.write(String.format("%.10f",precision)+";");
                        System.out.println(" " + metrica + "@" + cut + " = " + precision);
                        sum[aux] += precision;
                        break;
                    case "R":
                        bufCsv.write(String.format("%.10f",recall)+";");
                        System.out.println(" " + metrica + "@" + cut + " = " + recall);
                        sum[aux] += recall;
                        break;
                    case "MRR":
                        bufCsv.write(String.format("%.10f",rr)+";");
                        System.out.println(" " + metrica + "@" + cut + " = " + rr);
                        sum[aux] += rr;
                        break;
                    case "MAP":
                        bufCsv.write(String.format("%.10f",ap)+";");
                        System.out.println(" " + metrica + "@" + cut + " = " + ap);
                        sum[aux] += ap;
                        break;
                }

            }
            bufCsv.write("\n");
            System.out.println();
        }

        //Imprimimos los promedios
        bufCsv.write("Promedios;");
        System.out.println("Promedios:");
        int aux = 0;
        for(float promedio:sum){
            if(method.equals("jm"))
                System.out.println("Lambda:" + values[aux] + " -> " + promedio/(int2-int1+1));
            else
                System.out.println("K1:" + String.format("%.0f",values[aux]) + " -> " + promedio/(int2-int1+1));
            bufCsv.write(promedio/(int2-int1+1)+";");
            aux+=1;
        }

        //TEST
        System.out.println("\n--------------TEST--------------");
        //Sacamos lambda más optima
        int maxIndex = 0;
        for (int i = 1; i < sum.length; i++) {
            if (sum[i] > sum[maxIndex]) {
                maxIndex = i;
            }
        }
        double lambdaOpt = values[maxIndex];

        //Establecemos el searcher con la lambda optima
        if(method.equals("jm")) {
            searcher.setSimilarity(new LMJelinekMercerSimilarity((float) lambdaOpt));
            System.out.println("Lambda Optima:" + lambdaOpt);
        }else {
            searcher.setSimilarity(new BM25Similarity((float) lambdaOpt, 0.75f));
            System.out.println("K1 Optimo:" + String.format("%.0f",lambdaOpt));
        }

        //Creamos el CSV con la cabecera
        File testCSV = new File("TREC-COVID." + method + ".training." + int1 + "-" + int2 + ".test." + int3 + "-" + int4 + "."
                + metrica + cut + ".test.csv");
        FileWriter escritor2 = new FileWriter(testCSV);
        BufferedWriter buf2 = new BufferedWriter(escritor2);
        buf2.write(lambdaOpt + ";" + metrica + "@" + cut + "\n");

        //Probamos las queries de test con la lambda optima
        double sumatest = 0;
        for (int i = int3; i<=int4; i++){
            ArrayList<String> testRelevants = GetRelevants(i);

            String query = subQueries.get(i-1).toLowerCase();
            TopDocs topDocs = searcher.search(parser.parse(query), cut);



            //Sacamos las metricas de cada una
            float relevantesA = 0;
            float suMetr = 0;
            double rr = 0;
            for (int j = 0; j < topDocs.scoreDocs.length; ++j) {
                Document doc = reader.document(topDocs.scoreDocs[j].doc);
                String id = doc.get("id");
                if (testRelevants.contains(id)) {
                    relevantesA++;
                    suMetr += relevantesA / (j + 1);
                    if (rr == 0) {
                        rr = 1.0 / (j + 1);
                    }
                }
            }
            float precision = relevantesA/topDocs.scoreDocs.length;
            float recall = relevantesA / testRelevants.size();
            float ap = suMetr / testRelevants.size();

            //Imprimimos el ID de la query y su metrica
            buf2.write(i + ";");
            System.out.print("Query " + i);
            switch(metrica){
                case "P":
                    buf2.write(String.format("%.10f",precision)+"\n");
                    System.out.println(" " + metrica + "@" + cut + " = " + precision);
                    sumatest += precision;
                    break;
                case "R":
                    buf2.write(String.format("%.10f",recall)+"\n");
                    System.out.println(" " + metrica + "@" + cut + " = " + recall);
                    sumatest += recall;
                    break;
                case "MRR":
                    buf2.write(String.format("%.10f",rr)+"\n");
                    System.out.println(" " + metrica + "@" + cut + " = " + rr);
                    sumatest += rr;
                    break;
                case "MAP":
                    buf2.write(String.format("%.10f",ap)+"\n");
                    System.out.println(" " + metrica + "@" + cut + " = " + ap);
                    sumatest += ap;
                    break;
            }
        }
        //Imprimimos los promedios
        buf2.write("Promedio:;"+(sumatest/(int4-int3+1)));
        System.out.println("Promedio: "+(sumatest/(int4-int3+1)));

        //Cerramos los buffers
        bufCsv.close();
        writerCsv.close();
        buf2.close();
    }

    public static ArrayList<String> GetRelevants (int i) throws IOException {
        ArrayList<String> relevants = new ArrayList<>();
        BufferedReader bufferedReader = new BufferedReader(new FileReader("src/files/qrels/test.tsv"));
        String line = bufferedReader.readLine();
        String[] words;

        while ((line = bufferedReader.readLine()) != null) {
            words = line.split("\t");
            if(Integer.parseInt(words[0]) == i){
                if(Integer.parseInt(words[2]) > 0){
                    relevants.add(words[1].toLowerCase());
                }

            }
        }
        return relevants;
    }
}
