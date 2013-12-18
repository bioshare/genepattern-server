package org.genepattern.server.job.input.collection;

import org.genepattern.server.job.input.GroupId;
import org.genepattern.server.job.input.ParamValue;

public interface ParamValueVisitor {
    void start() throws Exception;
    void visitValue(GroupId groupId, ParamValue value) throws Exception;
    void finish(boolean withErrors);
}
