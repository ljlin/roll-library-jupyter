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

package roll.main.complement;

import java.util.List;

import automata.FiniteAutomaton;
import dk.brics.automaton.Automaton;
import mainfiles.RABIT;
import oracle.IntersectionCheck;
import roll.automata.FDFA;
import roll.automata.NBA;
import roll.automata.operations.FDFAOperations;
import roll.automata.operations.NBAOperations;
import roll.main.Options;
import roll.main.inclusion.UtilInclusion;
import roll.oracle.Teacher;
import roll.oracle.nba.sampler.NBAInclusionSampler;
import roll.oracle.nba.sampler.SamplerIndexedMonteCarlo;
import roll.query.Query;
import roll.query.QuerySimple;
import roll.table.HashableValue;
import roll.table.HashableValueBoolean;
import roll.table.HashableValueBooleanExactPair;
import roll.util.Pair;
import roll.util.Timer;
import roll.words.Alphabet;
import roll.words.Word;

/**
 * @author Yong Li (liyong@ios.ac.cn)
 * 
 * Yong Li, Andrea Turrini, Lijun Zhang and Sven Schewe
 *    "Learning to Complement Büchi Automata"
 * in VMCAI 2018
 * */

public class TeacherNBAComplement implements Teacher<FDFA, Query<HashableValue>, HashableValue> {

    private final NBA B;
    private final Options options;
    private final FiniteAutomaton rB;
    private final Alphabet alphabet;
    
    public TeacherNBAComplement(Options options, NBA nba) {
        assert options != null && nba != null;
        this.options = options;
        this.B = nba;
        this.alphabet = nba.getAlphabet();
        this.rB = UtilInclusion.toRABITNBA(nba);
    }
    
    @Override
    public HashableValue answerMembershipQuery(Query<HashableValue> query) {
        Timer timer = new Timer();
        timer.start();
        
        boolean result;
        Word prefix = query.getPrefix();
        Word suffix = query.getSuffix();
        
        if(suffix.isEmpty()) {
            return new HashableValueBoolean(false);
        }else {
            result = NBAOperations.accepts(B, prefix, suffix);
        }
        
        timer.stop();
        options.stats.timeOfMembershipQuery += timer.getTimeElapsed();
        ++ options.stats.numOfMembershipQuery; 
        return new HashableValueBoolean(!result); // reverse the result for Buechi automaton
    }
    
    public int numInterBandBF;
    public long timeInterBandBF;
    
    public int numInterBFCandBF;
    public long timeInterBFCandBF;
    
    public int numBFCLessB;
    public long timeBFCLessB;
    
    public boolean sampling = false;
    
    private Pair<Word, Word> getCounterexample(List<String> prefix, List<String> suffix) {
        int[] pre = new int[prefix.size()];
        for(int i = 0; i < pre.length; i ++) {
            pre[i] = alphabet.indexOf(prefix.get(i).charAt(0));
        }
        int[] suf = new int[suffix.size()];
        for(int i = 0; i < suf.length; i ++) {
            suf[i] = alphabet.indexOf(suffix.get(i).charAt(0));
        }
        return new Pair<>(alphabet.getArrayWord(pre), alphabet.getArrayWord(suf));
    }

    @Override
    public Query<HashableValue> answerEquivalenceQuery(FDFA hypothesis) {
        Timer timer = new Timer();
        timer.start();
        options.log.println("Translating FDFA to under Buchi automaton ...");
        Automaton dkBF = FDFAOperations.buildUnderNBA(hypothesis);
        NBA BF = NBAOperations.fromDkNBA(dkBF, alphabet);
        // record the constructed Buchi automaton
        options.stats.hypothesis = BF;
        ++ this.numInterBandBF;
        options.log.println("Checking the intersection of BF (" + BF.getStateSize() + ") and B ("+ B.getStateSize() + ")...");
        long t = timer.getCurrentTime();
        FiniteAutomaton rBF = UtilInclusion.toRABITNBA(BF);
        IntersectionCheck checker = new IntersectionCheck(rBF, rB);
//        NBAIntersectionCheck interCheck = new NBAIntersectionCheck(BF, B, true);
//        boolean isEmpty = interCheck.isEmpty();
        boolean isEmpty = checker.checkEmptiness();
        t = timer.getCurrentTime() - t;
        this.timeInterBandBF += t;
        if(options.verbose) {
            options.log.println("Hypothesis for complementation B");
            options.log.println(BF.toString());
        }
        Word prefix = null;
        Word suffix = null;
        boolean isEq = false, isInTarget = false;
        if(! isEmpty) {
            // we have omega word in FDFA which should not be there
            checker.computePath();
            Pair<Word, Word> pair = getCounterexample(checker.getPrefix(), checker.getSuffix());
            prefix = pair.getLeft();
            suffix = pair.getRight();
            isEq = false;
            isInTarget = true;
        } else {
            Automaton dkBFC = FDFAOperations.buildNegNBA(hypothesis);
            NBA BFC = NBAOperations.fromDkNBA(dkBFC, alphabet);
            options.log.println("Checking the intersection for B(F) (" + BF.getStateSize() + ") and B(F^c) ("
                    + BFC.getStateSize() + ")...");
            ++this.numInterBFCandBF;
            t = timer.getCurrentTime();
            FiniteAutomaton rBFC = UtilInclusion.toRABITNBA(BFC);
            checker = new IntersectionCheck(rBFC, rBF);
            isEmpty = checker.checkEmptiness();
            t = timer.getCurrentTime() - t;
            this.timeInterBFCandBF += t;

            if (!isEmpty) {
                // we have found counterexample now
                checker.computePath();
                Pair<Word, Word> pair = getCounterexample(checker.getPrefix(), checker.getSuffix());
                prefix = pair.getLeft();
                suffix = pair.getRight();
                isEq = false;
                isInTarget = NBAOperations.accepts(B, prefix, suffix);
            } else {
                // we have to resort to the equivalence check for hypothesisNotA
                ++this.numBFCLessB;
                options.log.println("Checking the inclusion between B(F^c) (" + BFC.getStateSize() + ") and B ("
                        + B.getStateSize() + ")...");
                if (options.verbose) {
                    options.log.println("B(F^c): \n" + BFC.toString());
                }
                // by sampler
                boolean hasCE = false;
                
                if(sampling) {
                    options.log.println("Sampling for a counterexample to the inclusion...");
                    SamplerIndexedMonteCarlo sampler = new SamplerIndexedMonteCarlo(options.epsilon, options.delta);
                    sampler.K = B.getStateSize();
                    Query<HashableValue> ceQuery = NBAInclusionSampler.isIncluded(BFC, B, sampler);
                    if (ceQuery != null) {
                        prefix = ceQuery.getPrefix();
                        suffix = ceQuery.getSuffix();
                        isInTarget = false;
                        isEq = false;
                        hasCE = true;
                    }
                }
                
                if(! hasCE) {
                    // by rabit
                    options.log.println("RABIT for a counterexample to the inclusion...");
                    t = timer.getCurrentTime();
                    boolean isIncluded = RABIT.isIncludedPar(rBFC, rB);
                    t = timer.getCurrentTime() - t;
                    this.timeBFCLessB += t;
                    String prefixStr = RABIT.getPrefix();
                    String suffixStr = RABIT.getSuffix();
                    if (isIncluded) {
                        isEq = true;
                    } else {
                        isInTarget = false;
                        // check whether it is in A
                        prefix = alphabet.getWordFromString(prefixStr);
                        suffix = alphabet.getWordFromString(suffixStr);
                        isEq = false;
                    }
                }
            }
        }
        
        options.log.println("Done for checking equivalence...");
        Query<HashableValue> query = null;
        isInTarget = ! isInTarget;
        
        if(isEq) {
            query = new QuerySimple<>(alphabet.getEmptyWord(), alphabet.getEmptyWord());
            query.answerQuery(new HashableValueBooleanExactPair(true, true));
        }else {
            query = new QuerySimple<>(prefix, suffix);
            query.answerQuery(new HashableValueBooleanExactPair(false, isInTarget));
        }
        
        timer.stop();
        options.stats.timeOfEquivalenceQuery += timer.getTimeElapsed();
        ++ options.stats.numOfEquivalenceQuery;
        options.stats.timeOfLastEquivalenceQuery = timer.getTimeElapsed();
        
        if(options.verbose) System.out.println("counter example = " + query);
        return query;
    }
    
    public void print() {
        final int indent = 30;
        options.log.println("#B(F)&B = " + numInterBandBF, indent, "    // #number of B(F) intersection with B");
        options.log.println("#B(F^c)&BF = " + numInterBFCandBF, indent, "    // #number of B(F^c) intersection with B(F)");
        options.log.println("#B(F^c)<=B = " + numBFCLessB, indent, "    // #number of B(F^c) included in B");
        options.log.println("#TB(F)&B = " + timeInterBandBF, indent, "    // time for B(F) intersection with B");
        options.log.println("#TB(F^c)&BF = " + timeInterBFCandBF, indent, "    // time for B(F^c) intersection with B(F)");
        options.log.println("#TB(F^c)<=B = " + timeBFCLessB, indent, "    // time for B(F^c) included in B");
    }

}
