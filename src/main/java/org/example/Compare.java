package org.example;

import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.WilcoxonSignedRankTest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

public class Compare {
    public static void main(String[] args) throws IOException {
        String usage = "-test [t|wilcoxon alpha] -result [primer_csv segundo_csv] " + "\n\n";
        String testType = null;
        double alpha = 0;
        String results1 = null;
        String results2 = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-results": {
                    results1 = args[++i];
                    results2 = args[++i];
                    break;
                }
                case "-test": {
                    testType = args[++i];
                    alpha = Double.parseDouble(args[++i]);
                    break;
                }
                default:
                    throw new IllegalArgumentException("unknown parameter " + args[i]);
            }
        }

        if (results1 == null || results2 == null || testType == null) {
            throw new IllegalArgumentException("Usage: " + usage);
        }


        File primerCsv = new File(results1);
        FileReader lectorP = new FileReader(primerCsv);
        FileReader lectorPl = new FileReader(primerCsv);
        BufferedReader bufPl = new BufferedReader(lectorPl);
        BufferedReader bufP = new BufferedReader(lectorP);
        long lineasPrimero = bufPl.lines().count();
        double[] atribPrimero = new double[5000];
        ArrayList<String> elem = new ArrayList<>();
        bufP.readLine();//para no añadir la primera linea
        for (int i = 1; i < lineasPrimero - 1; ++i) {
            elem.add(bufP.readLine().split(";")[1].replace(",", "."));
        }
        for (int j = 0; j < elem.size(); ++j){
            atribPrimero[j] = (Double.parseDouble(elem.get(j)));
        }


        File segundoCsv = new File(results2);
        FileReader lectorS = new FileReader(segundoCsv);
        FileReader lectorSl = new FileReader(segundoCsv);
        BufferedReader bufSl = new BufferedReader(lectorSl);
        long lineasSegundo =  bufSl.lines().count();
        BufferedReader bufS = new BufferedReader(lectorS);
        double[] atribSegundo = new double[5000];
        bufS.readLine();
        ArrayList<String> elem2 = new ArrayList<>();
        for (int i = 1; i < lineasSegundo - 1; ++i) {
            elem2.add(bufS.readLine().split(";")[1].replace(",", "."));
        }
        for (int j = 0; j < elem2.size(); ++j){
            atribSegundo[j] = Double.parseDouble(elem2.get(j));
        }
        if (testType.equals("t")) {
            TTest ttest = new TTest();
            double test = new TTest().pairedTTest(atribPrimero, atribSegundo);
            if (alpha < test)
                System.out.println("No satisfactorio: "+ ttest.pairedTTest(atribPrimero, atribSegundo));
            else
                System.out.println("Satisfactorio, comparacion t: " + ttest.pairedTTest(atribPrimero, atribSegundo));
        } else {
            WilcoxonSignedRankTest wilc = new WilcoxonSignedRankTest();
            double test = wilc.wilcoxonSignedRank(atribPrimero, atribSegundo);
            if (alpha < test)
                System.out.println("No es satisfactorio: "+ wilc.wilcoxonSignedRankTest(atribPrimero, atribSegundo, true));
            else
                System.out.println("Satisfactorio, comparacion wilcoxon: " + wilc.wilcoxonSignedRankTest(atribPrimero, atribSegundo, true));
        }
    }
}

