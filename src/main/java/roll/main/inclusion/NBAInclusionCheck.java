/* Copyright (c) 2016, 2017                                               */
/*       Institute of Software, Chinese Academy of Sciences               */
/* This file is part of ROLL, a Regular Omega Language Learning library.  */
/* ROLL is free software: you can redistribute it and/or modify           */
/* it under the terms of the GNU General Public License as published by   */
/* the Free Software Foundation, either version 3 of the License, or      */
/* (at your option) any later version.                                    */

/* This program is distributed in the hope that it will be useful,        */
/* but WITHOUT ANY WARRANTY; without even the implied warranty of         */
/* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the          */
/* GNU General Public License for more details.                           */

/* You should have received a copy of the GNU General Public License      */
/* along with this program.  If not, see <http://www.gnu.org/licenses/>.  */

package roll.main.inclusion;

import automata.FiniteAutomaton;
import oracle.EmptinessChecker;
import roll.automata.NBA;
import roll.automata.operations.NBAOperations;
import roll.learner.fdfa.LearnerFDFA;
import roll.learner.nba.lomega.UtilLOmega;
import roll.learner.nba.lomega.translator.Translator;
import roll.learner.nba.lomega.translator.TranslatorFDFAUnder;
import roll.main.Options;
import roll.oracle.nba.sampler.SamplerIndexedMonteCarlo;
import roll.query.Query;
import roll.table.HashableValue;
import roll.table.HashableValueBoolean;
import roll.util.Pair;
import roll.util.Timer;
import roll.words.Word;

/**
 * @author Yong Li (liyong@ios.ac.cn)
 * 
 * Learning algorithm to check the inclusion for two given Buechi automata A and B
 * , i.e., L(A) <= L(B)
 * 
 * */

public class NBAInclusionCheck {
    
    public static void main(String[] args) {
        
    
        FiniteAutomaton aut1 = null;
        FiniteAutomaton aut2 = null;
        
        Options options = new Options();
        // default number
        options.epsilon = 0.0018;
        options.delta = 0.001;
        // parse input arguments
        for(int i = 0; i < args.length; i ++) {
            if(args[i].compareTo("-h")==0) {
                helper(options);
            }
        }
        
        if(args.length < 2) {
            helper(options);
        }

        // for learning
        for(int i = 0; i < args.length; i ++) {
            
            if(args[i].compareTo("-v") == 0){
                options.verbose=true;
                continue;
            }
            if(args[i].compareTo("-table") == 0) {
                options.structure = roll.main.Options.Structure.TABLE;
                continue;
            }
            if(args[i].compareTo("-tree") == 0) {
                options.structure = roll.main.Options.Structure.TREE;
                continue;
            }
            if(args[i].compareTo("-syntactic") == 0) {
                options.algorithm = roll.main.Options.Algorithm.SYNTACTIC;
                continue;
            }
            if(args[i].compareTo("-recurrent") == 0) {
                options.algorithm = roll.main.Options.Algorithm.RECURRENT;
                continue;
            }
            if(args[i].compareTo("-periodic") == 0) {
                options.algorithm = roll.main.Options.Algorithm.PERIODIC;
                continue;
            }
            if(args[i].compareTo("-sameq") == 0) {
                options.epsilon = Double.parseDouble(args[i+1]);
                options.delta = Double.parseDouble(args[i+2]);
                i += 2;
                continue;
            }
            if(args[i].endsWith(".ba")) {
                aut1 = new FiniteAutomaton(args[i]);
                aut2 = new FiniteAutomaton(args[i+1]);
                i += 1;
                continue;
            }
        }
        assert aut1 != null && aut2 != null;
        options.automaton = Options.TargetAutomaton.FDFA;
        Timer timer = new Timer();
        timer.start();
        options.log.println("Aut A : # of Trans. "+aut1.trans+", # of States "+aut1.states.size()+".");
        options.log.println("Aut B : # of Trans. "+aut2.trans+", # of States "+aut2.states.size()+".");
        boolean isEmpty1 = UtilInclusion.removeDeadStates(aut1);
        if (isEmpty1) {
            options.log.println("Included");
            timer.stop();
            options.log.println("Total checking time: " + timer.getTimeElapsed()/ 1000.0 + " secs");
            System.exit(0);
        }
        boolean isEmpty2 = UtilInclusion.removeDeadStates(aut2);
        if (isEmpty2 && !isEmpty1) {
            EmptinessChecker checker = new EmptinessChecker(aut1, aut1.F, aut1.states);
            boolean empty = checker.isEmpty();
            if (!empty) {
                checker.findpath();
                options.log.println("Not Included");
                options.log.println("prefix: " + checker.getWordFinder().getWordPrefix());
                options.log.println("suffix: " + checker.getWordFinder().getWordSuffix());
                timer.stop();
                options.log.println("Total checking time: " + timer.getTimeElapsed()/ 1000.0 + " secs");
                System.exit(0);
            }
        }
        
        // now we have to first collect the symbols
        Symbol symbol = new Symbol();
        for(String symb : aut1.alphabet) {
            symbol.addSymbol(symb);
        }
        for(String symb : aut2.alphabet) {
            symbol.addSymbol(symb);
        }
        
        // now we are ready to replace the symbols on the transitions
        NBA A = symbol.toNBA(aut1);
        NBA B = symbol.toNBA(aut2);
        options.log.println("Aut A (after processing) : # of Trans. "+aut1.trans+", # of States "+aut1.states.size()+".");
        options.log.println("Aut B (after processing) : # of Trans. "+aut2.trans+", # of States "+aut2.states.size()+".");
        options.log.println("Start to prove inclusion via sampling...");
        SamplerIndexedMonteCarlo sampler = new SamplerIndexedMonteCarlo(options.epsilon, options.delta);
        long num = sampler.getSampleSize();
        sampler.setNBA(A);
        options.log.println("Trying " + num + " samples from A automaton...");
        for (int i = 0; i < num; i++) {
            Pair<Pair<Word, Word>, Boolean> result = sampler.getRandomLasso();
            Pair<Word, Word> word = result.getLeft();
            boolean needCheck = false;
            if (result.getRight()) {
                needCheck = true;
            } else {
                needCheck = NBAOperations.accepts(A, word.getLeft(), word.getRight());
            }
            if (needCheck) {
                boolean acc = NBAOperations.accepts(B, word.getLeft(), word.getRight());
                if (!acc) {
                    options.log.println("Not included");
                    options.log.println("prefix: ");
                    for(int letterNr = 0; letterNr < word.getLeft().length(); letterNr ++) {
                        options.log.print("" + symbol.getSymbol(word.getLeft().getLetter(letterNr)) + ",");
                    }
                    options.log.println("\nsuffix: ");
                    for(int letterNr = 0; letterNr < word.getRight().length(); letterNr ++) {
                        options.log.print("" + symbol.getSymbol(word.getRight().getLetter(letterNr)) + ",");
                    }
                    options.log.println("");
                    timer.stop();
                    options.log.println("Total checking time: " + timer.getTimeElapsed()/ 1000.0 + " secs");
                    System.exit(0);
                }
            }
        }
        timer.stop();
        options.log.println("Total sampling time: " + timer.getTimeElapsed()/ 1000.0 + " secs");
        options.log.println("Possibly Included and we will use learning algorithm to prove inclusion...");
        // learning algorithm
        TeacherNBAInclusion teacher = new TeacherNBAInclusion(options, symbol, A, B);
        LearnerFDFA learner = UtilLOmega.getLearnerFDFA(options, symbol.getAlphabet(), teacher);
        // learning loop
        Timer timerTotal = new Timer();
        timerTotal.start();
        options.log.println("Start learning...");
        timer.start();
        learner.startLearning();
        timer.stop();
        options.stats.timeOfLearner += timer.getTimeElapsed();
        boolean result = false;
        while(! result ) {
            if(options.verbose) options.log.println("learner output: " + learner.toString());
            Query<HashableValue> query = teacher.answerEquivalenceQuery(learner.getHypothesis());
            // get out of the loop
            HashableValue answer = query.getQueryAnswer();
            if(answer.getLeft().equals(true)) {
                break;
            }
            // lazy equivalence check is implemented here
            Translator translator = new TranslatorFDFAUnder(learner);
            query.answerQuery(new HashableValueBoolean(answer.getRight()));
            translator.setQuery(query);
            while(translator.canRefine()) {
                Query<HashableValue> ceQuery = translator.translate();
                timer.start();
                learner.refineHypothesis(ceQuery);
                timer.stop();
                options.stats.timeOfLearner += timer.getTimeElapsed();
                if(options.verbose) options.log.println("learner output: " + learner.toString());
                // if do not set lazy eq check or it is learnerBuechi
                if(options.optimization != Options.Optimization.LAZY_EQ) break;
            }
        }
        timerTotal.stop();
        options.log.println("Learning completed...");
        teacher.print();
        options.log.println(timerTotal.getTimeElapsed()+ " ms elapsed...");
    }

    public static void helper(Options options) {
        options.log.println("BUNE v1 (BUechi inclusioN chEck base on learning)");
        options.log.println("Usage: java -jar bune.jar aut1.ba aut2.ba [options]");
        final int indent = 12;
        options.log.println("Recommended use", indent, "java -jar bune.jar aut1.ba aut2.ba -aut -table -recurrent");
        options.log.println("             or", indent, "java -jar bune.jar aut1.ba aut2.ba -aut -table -periodic");
        options.log.println("             or", indent, "java -jar bune.jar aut1.ba aut2.ba -aut -table -syntactic");
        options.log.println("\noptions:");
        
        options.log.println("-h", indent, "Show this page");
        options.log.println("-v", indent, "Verbose mode");
        options.log.println("-test k n", indent, "Test bune with k randomly generated Buechi automata of n states");
        options.log.println("-aut", indent, "Use RABIT or DK package tool as the teacher");
        options.log.println("-sameq e d", indent, "Sampling as the teacher to check equivalence of two BA automata");
        options.log.println("", indent + 4, "e - the probability that equivalence check is not correct");
        options.log.println("", indent + 4, "d - the probability of the confidence for equivalence check");
        options.log.println("-tree", indent, "Use tree-based data structure in learning (Default)");
        options.log.println("-table", indent, "Use table-based data structure in learning");
        options.log.println("-periodic", indent, "Use peridoc FDFA to learn Omega regular language");
        options.log.println("-recurrent", indent, "Use recurrent FDFA to learn Omega regular language");
        options.log.println("-syntactic", indent, "Use syntactic FDFA to learn Omega regular language");
    }

}
