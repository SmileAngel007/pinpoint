/*
 * Copyright 2015 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.navercorp.pinpoint.web.calltree.span;

/**
 * 
 * @author jaehong.kim
 *
 */
public class SpanAsyncCallTree implements CallTree {

    private final SpanCallTree tree;

    public SpanAsyncCallTree(final SpanAlign spanAlign) {
        tree = new SpanCallTree(spanAlign);
    }

    @Override
    public CallTreeNode getRoot() {
        if (!tree.getRoot().hasChild()) {
            return null;
        }

        return tree.getRoot().getChild();
    }

    @Override
    public CallTreeIterator iterator() {
        return new CallTreeIterator(getRoot());
    }

    @Override
    public boolean isEmpty() {
        CallTreeNode root = getRoot();
        if (root == null) {
            return true;
        }
        return root.getValue() == null;
    }

    @Override
    public void add(CallTree tree) {
        this.tree.add(tree);
    }

    @Override
    public void add(int depth, SpanAlign spanAlign) {
        tree.add(depth, spanAlign);
    }

    @Override
    public void sort() {
        tree.sort();
    }
}