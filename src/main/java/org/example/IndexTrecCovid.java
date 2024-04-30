package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.LMJelinekMercerSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.index.*;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class IndexTrecCovid {

    public static void main(String[] args) throws IOException {
        String usage = "java org.apache.lucene.demo.IndexTrecCovid"
                + " [-openMode <openmode>] [-index <ruta>] [-docs <ruta>]  [-indexingModel n jm <lambda> | bm25 <k1>]\n\n";

        String openmode = null;
        String indexPath = null;
        String docsPath = null;

        String indexingModel = null;
        float lambda = 0;
        //Recorremos el bucle buscando los argumentos
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-openMode":
                    openmode = args[++i];
                    if (openmode == null){
                        throw new IllegalArgumentException("openmode not valid: " + args[i] + "\n" + usage);
                    }
                    break;
                case "-index":
                    indexPath = args[++i];
                    if (indexPath == null) {
                        throw new IllegalArgumentException("index not valid: " + args[i] + "\n" + usage);
                    }
                    break;

                case "-docs":
                    docsPath = args[++i];
                    if (docsPath == null) {
                        throw new IllegalArgumentException("docs not valid: " + args[i] + "\n" + usage);
                    }
                    break;
                //en este caso buscamos que se inserto tambien el lambda
                case "-indexingModel":
                    indexingModel = args[++i];
                    if (indexingModel == null) {
                        throw new IllegalArgumentException("docs not valid: " + args[i] + "\n" + usage);
                    } else if (indexingModel.equals("jm") || indexingModel.equals("bm25")) {
                        lambda = Float.parseFloat(args[++i]);
                    }else {
                        throw new IllegalArgumentException("indexingModel not valid: " + args[i] + "\n" + usage);
                    }
                    break;

                default:
                    throw new IllegalArgumentException("unknown args: " + args[i] + "\n" + usage);
            }
        }

        if (!openmode.equals("create") && !openmode.equals("append") && !openmode.equals("create_or_append")) {
            throw new IllegalArgumentException("invalid openMode: " +
                    "create, append, create_or_append");
        }


        Date start = new Date();

        try {
            System.out.println("Indexing to directory '" + indexPath + "'...");

            assert (indexPath != null);
            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new StandardAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            switch (indexingModel) {
                case "jm": {
                    LMJelinekMercerSimilarity jm = new LMJelinekMercerSimilarity(lambda);
                    iwc.setSimilarity(jm);
                    break;
                }
                case "bm25": {
                    BM25Similarity bm25 = new BM25Similarity(lambda, 0.75f);
                    iwc.setSimilarity(bm25);
                    break;
                }
                default: {
                    throw new IllegalArgumentException("indexingModel????: " + indexingModel);
                }
            }
            switch (openmode) {
                case "create":
                    iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
                    break;
                case "create_or_append":
                    iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
                    break;
                case "append":
                    iwc.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
                    break;
                default: {
                    System.out.println("OpenMode unknown: ");
                    System.exit(-1);
                }
            }

            ObjectMapper objectMapper = new ObjectMapper();

            File directory = new File(docsPath);
            File[] files = directory.listFiles((file, name) -> name.equals("corpus.jsonl"));

            File corpusFile;
            // Verificar si se encontró el archivo corpus.jsonl
            if (files != null) {
                corpusFile = files[0];
            }
            else{
                throw new IOException("corpus.json not found");
            }

            BufferedReader br = new BufferedReader(new FileReader(corpusFile));
            String line;
            IndexWriter iWriter = new IndexWriter(dir, iwc);

            while ((line = br.readLine()) != null) {
                // Convertir cada línea a un JsonNode
                JsonNode jsonNode = objectMapper.readTree(line);

                // Extraer los atributos requeridos
                String id = jsonNode.path("_id").asText();
                String title = jsonNode.path("title").asText();
                String text = jsonNode.path("text").asText();
                String url = jsonNode.path("metadata").path("url").asText();
                String pubmedId = jsonNode.path("metadata").path("pubmed_id").asText();

                indexDoc(iWriter, id, title, text, url, pubmedId);
                }

                iWriter.close();

                Date end = new Date();

                try (IndexReader reader = DirectoryReader.open(dir)) {
                    System.out.println("Indexed " + reader.numDocs() + " documents in " + (end.getTime() - start.getTime())
                            + " milliseconds");
                }

            } catch (IOException e) {
                throw new IOException(e);
            }

    }

    static void indexDoc(IndexWriter writer, String id, String title, String text, String url, String pubmedId) throws IOException {
        Document doc = new Document();

        doc.add(new StringField("id", id, Field.Store.YES));
        doc.add(new TextField("title", title, Field.Store.YES));
        doc.add(new TextField("text", text, Field.Store.YES));
        doc.add(new TextField("url", url, Field.Store.YES));
        doc.add(new TextField("pubmedId", pubmedId, Field.Store.YES));


        if (writer.getConfig().getOpenMode() == IndexWriterConfig.OpenMode.CREATE) {
            System.out.println("adding " + "doc" + id);
            writer.addDocument(doc);
        } else {
            System.out.println("updating " + "doc" + id);
            writer.updateDocument(new Term("path", "doc" + id), doc);
        }
    }


}