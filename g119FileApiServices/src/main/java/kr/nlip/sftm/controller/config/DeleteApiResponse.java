package kr.nlip.sftm.controller.config;
 
public class DeleteApiResponse {
    private String msfrtnid;
    private String deleteApiUri;
    private String type;
    
    public DeleteApiResponse(String msfrtnid, String deleteApiUri, String type) {
        this.msfrtnid = msfrtnid;
        this.deleteApiUri = deleteApiUri;
        this.type = type;
    }

	public String getMsfrtnid() {
		return msfrtnid;
	}

	public void setMsfrtnid(String msfrtnid) {
		this.msfrtnid = msfrtnid;
	}

	public String getDeleteApiUri() {
		return deleteApiUri;
	}

	public void setDeleteApiUri(String deleteApiUri) {
		this.deleteApiUri = deleteApiUri;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
    
}


