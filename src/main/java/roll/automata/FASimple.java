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

package roll.automata;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

import com.google.common.collect.ImmutableBiMap;
import jupyter.Displayer;
import jupyter.Displayers;
import jupyter.MIMETypes;
import roll.NativeTool;
import roll.util.sets.ISet;
import roll.util.sets.UtilISet;
import roll.words.Alphabet;

/**
 * simple FA like DFA, NFA and NBA
 * @author Yong Li (liyong@ios.ac.cn)
 * */
public abstract class FASimple implements FA {
//    static {
//        FASimple.register();
//    }

    protected final ArrayList<StateFA> states;
    protected final Alphabet alphabet;
    protected int initialState;
    protected final ISet finalStates;
    protected Acc acceptance;
//    public List<Integer> colored;
    public int stateToSplit = -1;
    public int stateToAdd   = -1;
    public String title;
//    public Object previous;

    public FASimple(final Alphabet alphabet) {
        this.alphabet = alphabet;
        this.states = new ArrayList<>();
        this.finalStates = UtilISet.newISet();
    }
    
    public Alphabet getAlphabet() {
        return alphabet;
    }
    
    public int getStateSize() {
        return states.size();
    }
    
    public int getAlphabetSize() {
        return alphabet.getLetterSize();
    }
    
    public StateFA createState() {
        StateFA state = makeState(states.size());
        states.add(state);
        return state;
    }

    public void setInitial(int state) {
        initialState = state;
    }
    
    public boolean isInitial(int state) {
        return state == initialState;
    }
    
    public void setInitial(State state) {
        setInitial(state.getId());
    }
    
    public StateFA getState(int state) {
        assert checkValidState(state);
        return states.get(state);
    }
    
    public int getInitialState() {
        return initialState;
    }
    
    public void setFinal(int state) {
        assert checkValidState(state);
        finalStates.set(state);
    }

    public void setFinal(State state) {
        this.setFinal(state.getId());
    }

    public ISet getFinalStates() {
        return finalStates.clone();
    }
    
    public boolean isFinal(int state) {
        assert checkValidState(state);
        return finalStates.get(state);
    }
    
    protected abstract StateFA makeState(int index);
    
    protected boolean checkValidState(int state) {
        return state >= 0 && state < states.size();
    }
    
    protected boolean checkValidLetter(int letter) {
        return letter >= 0 && letter < getAlphabetSize();
    }
    
    @Override
    public Acc getAcc() {
        return acceptance;
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph {\n");
        int startNode = this.getStateSize();
        for (int node = 0; node < this.getStateSize(); node++) {
            builder.append(this.getState(node).toString());
        }
        builder.append("  " + startNode + " [label=\"\", shape = plaintext];\n");
        builder.append("  " + startNode + " -> " + this.getInitialState() + " [label=\"\"];\n");
        builder.append("}\n");
        return builder.toString();
    }


    public String toString(List<String> apList) {
        StringBuilder builder = new StringBuilder();
        builder.append("digraph {\n");
        int startNode = this.getStateSize();
        for (int node = 0; node < this.getStateSize(); node++) {
            builder.append(this.getState(node).toString(apList));
        }
        builder.append("  " + startNode + " [label=\"\", shape = plaintext];\n");
        builder.append("  " + startNode + " -> " + this.getInitialState() + " [label=\"\"];\n");
        builder.append("}\n");
        return builder.toString();
    }
    public String toDot() {
        ImmutableBiMap<Integer,String> stateColor = ImmutableBiMap.of(0,"orangered",1,"skyblue");

        StringBuilder builder = new StringBuilder();
        builder.append("digraph {\n");
        if (title != null){
            builder.append("  label=\"" + title + "\";\n");
        }
        int startNode = this.getStateSize();
        //
        List<String> apList = new ArrayList<>();
        for(int i = 0; i < alphabet.getLetterSize(); i ++) {
            apList.add(alphabet.getLetter(i) + "");
        }
        for (int node = 0; node < this.getStateSize(); node++) {
            String fillColor = (

                    node == stateToSplit ? stateColor.get(0) :
                    node >= stateToAdd && stateToAdd > 0   ? stateColor.get(1) :
                    null
            );
            builder.append(this.getState(node).toDot(apList,fillColor));
        }
        builder.append("  " + startNode + " [label=\"\", shape = plaintext];\n");
        builder.append("  " + startNode + " -> " + this.getInitialState() + " [label=\"\"];\n");
        builder.append("}\n");
        return builder.toString();
    }

    public String toBA() {
        StringBuilder builder = new StringBuilder();
        builder.append("[" + getInitialState() + "]\n");
        for (int node = 0; node < this.getStateSize(); node++) {
            builder.append(this.getState(node).toBA());
        }
        for (int acc : finalStates) {
            builder.append("[" + acc + "]\n");
        }
        return builder.toString();
    }
    public String toSVG() throws IOException {
        return NativeTool.Dot2SVG(this.toDot());
    }

    static void register(){
        Displayers.register(FASimple.class, new Displayer<FASimple>() {
            @Override
            public Map<String, String> display(FASimple automaton) {
                return new HashMap<String, String>() {{
                    try {
//                        if (automaton.previous == null && automaton instanceof FASimple){
                            put(MIMETypes.HTML,automaton.toSVG());
//                        }
//                        else {
//                            FASimple previous = (FASimple) automaton.previous;
//                            String HTML =
//                                    "<table border=\"1\" cellspacing=\"0\" bordercolor=\"#000000\"  style=\"border-collapse:collapse;\">\n" +
//                                    "  <tr>\n" +
//                                    "    <th> Previous Hypothesis</th>\n" +
//                                    "    <th> Current Hypothesis </th>\n" +
//                                    "  </tr>\n" +
//                                    "  <tr>\n" +
//                                    "    <td>%s</td>\n" +
//                                    "    <td>%s</td>\n" +
//                                    "  </tr>\n" +
//                                    "</table>";
//                            put(MIMETypes.HTML,String.format(HTML,previous.toSVG(),automaton.toSVG()));
//                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }};
            }
        });
    }
}
