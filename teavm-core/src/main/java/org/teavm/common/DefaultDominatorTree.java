/*
 *  Copyright 2013 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.teavm.common;

/**
 *
 * @author Alexey Andreev
 */
class DefaultDominatorTree implements DominatorTree {
    private LCATree lcaTree;
    private int[] nodes;
    private int[] unodes;

    public DefaultDominatorTree(int[] dominators, int[] vertices) {
        lcaTree = new LCATree(dominators.length + 1);
        nodes = new int[dominators.length + 1];
        unodes = new int[dominators.length + 1];
        nodes[0] = -1;
        for (int i = 0; i < dominators.length; ++i) {
            int v = vertices[i];
            if (v < 0) {
                continue;
            }
            int dom = nodes[dominators[v] + 1];
            int node = lcaTree.addNode(dom);
            nodes[v + 1] = node;
            unodes[node] = v;
        }
    }

    @Override
    public boolean directlyDominates(int a, int b) {
        a = nodes[a + 1];
        b = nodes[b + 1];
        return lcaTree.lcaOf(a, b) == a;
    }

    @Override
    public int commonDominatorOf(int a, int b) {
        return unodes[lcaTree.lcaOf(nodes[a + 1], nodes[b + 1])];
    }

    @Override
    public boolean dominates(int a, int b) {
        a = nodes[a + 1];
        b = nodes[b + 1];
        return lcaTree.lcaOf(a, b) == a;
    }

    @Override
    public int immediateDominatorOf(int a) {
        if (a == 0) {
            return -1;
        }
        int result = lcaTree.parentOf(nodes[a + 1]);
        return result >= 0 ? unodes[result] : -1;
    }
}
