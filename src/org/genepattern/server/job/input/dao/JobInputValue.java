package org.genepattern.server.job.input.dao;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

import org.genepattern.server.job.input.GroupId;
import org.genepattern.server.job.input.ParamId;
import org.genepattern.server.job.input.ParamValue;

/**
 * Hibernate mapping class for saving job input values.
 * @author pcarr
 *
 */
@Entity
@Table(name="job_input_value")
@IdClass(JobInputValuePk.class)
public class JobInputValue {
    public static JobInputValue create(final Integer gpJobNo, final ParamId paramId, final int idx, final GroupId groupId, final ParamValue paramValue) {
        JobInputValue v=new JobInputValue();
        v.gpJobNo=gpJobNo;
        v.pname=paramId.getFqName();
        v.idx=idx;
        v.pvalue=paramValue.getValue();
        if (groupId != null) {
            v.groupId=groupId.getGroupId();
            v.groupName=groupId.getName();
        }
        return v;
    }
    // ---  primary key fields -------
    @Id
    @Column(name="gp_job_no", nullable=false)
    private Integer gpJobNo;
    @Id
    @Column(nullable=false)
    private String pname;
    @Id
    @Column(nullable=false)
    private Integer idx=1;
    // --- end primary key fields ---
    @Column(nullable=true)
    private String pvalue;
    @Column(name="group_id", nullable=false)
    private String groupId="";
    @Column(name="group_name", nullable=false)
    private String groupName="";
    
    private JobInputValue() {
    }

    public Integer getGpJobNo() {
        return gpJobNo;
    }

    public String getPname() {
        return pname;
    }


    public String getPvalue() {
        return pvalue;
    }


    public Integer getIdx() {
        return idx;
    }


    public String getGroupId() {
        return groupId;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGpJobNo(Integer gpJobNo) {
        this.gpJobNo = gpJobNo;
    }

    private void setPname(String pname) {
        this.pname = pname;
    }
    private void setPvalue(String pvalue) {
        this.pvalue = pvalue;
    }
    private void setIdx(Integer idx) {
        this.idx = idx;
    }
    private void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    private void setGroupName(String groupName) {
        this.groupName = groupName;
    }

}