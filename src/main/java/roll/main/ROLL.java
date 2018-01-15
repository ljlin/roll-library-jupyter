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

package roll.main;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

import roll.automata.NBA;
import roll.automata.operations.NBAGenerator;
import roll.automata.operations.NBAOperations;
import roll.parser.Parser;
import roll.parser.UtilParser;
import roll.util.Timer;

/**
 * 
 * Main entry of the tool Regular Omega Language Learning Library
 * 
 * 
 * @author Yong Li (liyong@ios.ac.cn)
 * */
public final class ROLL {
    
    public static void main(String[] args) {
        // select mode to execute
        CLParser clParser = new CLParser();
        clParser.prepareOptions(args);
        Options options = clParser.getOptions();
        options.log.println("\n" + options.toString());
        switch(options.runningMode) {
        case TEST:
            runTestMode(options);
            break;
        case INTERACTIVE:
            runInteractiveMode(options);
            break;
        case AUTOMATIC:
            runAutomaticMode(options, false);
            break;
        case SAMPLING:
            runAutomaticMode(options, true);
        default :
                options.log.err("Incorrect running mode.");
        }
    }
    
    private static void runTestMode(Options options) {
        final int numLetter = 2;
        for(int n = 0; n < options.numOfTests; n ++) {
            options.log.println("Testing case " + (n + 1) + " ...");
            NBA nba = NBAGenerator.getRandomNBA(options.numOfStatesForTest, numLetter);
            try{
                System.out.println("target: \n" + nba.toString());
                Executor.executeRABIT(options, nba);
            }catch (Exception e)
            {
                e.printStackTrace();
                options.log.err("Exception occured, Learning aborted...");
                System.out.println(nba.toString());
                System.exit(-1);
            }
            options.log.info("Done for case " + (n + 1));
        }
    }
    
    private static void runInteractiveMode(Options options) {
//        PlayExecution.execute();
    }
    
    private static void runAutomaticMode(Options options, boolean sampling) {

        Timer timer = new Timer();
        timer.start();
        // prepare the parser
        Parser parser = UtilParser.prepare(options, options.inputFile, options.format);
        NBA target = parser.parse();
        options.stats.numOfLetters = target.getAlphabetSize();
        options.stats.numOfStatesInTraget = target.getStateSize();
//        parser.print(target, System.out);
        // learn the target automaton
        
        if(sampling) {
            Executor.executeSampler(options, target);
        }else {
            Executor.executeRABIT(options, target);
        }
        timer.stop();
        options.stats.timeInTotal = timer.getTimeElapsed();
        // output target automaton
        if(options.outputFile != null) {
            try {
                parser.print(options.stats.hypothesis, new FileOutputStream(new File(options.outputFile)));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }else {
            options.log.println("\ntarget automaton:");
            parser.print(target, options.log.getOutputStream());
            options.log.println("\nhypothesis automaton:");
            parser.print(options.stats.hypothesis, options.log.getOutputStream());
        }
        parser.close();
        // output statistics
        options.stats.numOfTransInTraget = NBAOperations.getNumberOfTransitions(target);
        options.stats.numOfTransInHypothesis = NBAOperations.getNumberOfTransitions(options.stats.hypothesis);
        options.stats.print();
        
    }

}
