package kr.nlip.sftm.controller;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.geosolutions.geoserver.rest.GeoServerRESTPublisher;
import kr.nlip.sftm.VO.NlipVO;
import kr.nlip.sftm.VO.TestVo;
import kr.nlip.sftm.controller.config.DeleteApiResponse;
import kr.nlip.sftm.controller.config.FileUploadResponse;
import kr.nlip.sftm.service.FileUploadDownloadService;
import kr.nlip.sftm.service.TestService;
import lombok.extern.slf4j.Slf4j;

@RestController
@PropertySource(value = "application.properties", encoding = "UTF-8")
@Slf4j
public class G119FileApiController {

	@Value("${geoserverUrl}")
	String geoserverUrl;
	
	@Value("${geoserverAdmin}")
	String geoserverAdmin;
	
	@Value("${geoserverPw}")
	String geoserverPw;

	@Value("${file.upload-dir}")
	String updDir;
	
	@Autowired 
	TestService testService;
	
	@Autowired
	private ObjectMapper objectMapper;
	
	@Autowired
    private FileUploadDownloadService service;
	
	private static String errFilePath = "logs"+File.separatorChar+"Error.log";
	private static String stFilePath = "logs"+File.separatorChar+"status.log";
	
	/******************************************************TEST함수****************************************/
	/******************************************************프로젝트사용시 TEST함수로 구동확인*********************/
    @RequestMapping("/testView.do")
    public ModelAndView test(ModelAndView mav) {
    	System.out.println("testView.do API 호출");
        mav.setViewName("testView");
        mav.addObject("message","스프링 부트 애플리케이션 테스트");
        List<TestVo> testList = testService.selectTest();
    	System.out.println("testView.do API 호출" + testList);
        mav.addObject("list", testList);
        return mav;
    }
    
    @PostMapping("/fileTest.do")
    public void test(@RequestParam MultipartFile clipFile) {
    	System.out.println("testView.do API 호출" + clipFile.getName());
    }
    /******************************************************TEST함수****************************************/
    
    //업로드파일(단일)
    @RequestMapping(value="/nlipUploadFile", method = {RequestMethod.GET, RequestMethod.POST})
    public FileUploadResponse uploadFile(@RequestParam(value= "file", required= false) MultipartFile file, @RequestParam(value= "filepath", required= false) String filepath, @RequestParam(value= "type", required= false) String type ) {
        String fileName = service.storeFile(file, filepath, type);
        statusLogSaver("-------------------------------------");
        statusLogSaver("uploadFile Call");
        statusLogSaver("fileName : "+ fileName);
        statusLogSaver("-------------------------------------");
        
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                                .path("/nlipUploadFile/")
                                .path(fileName)
                                .toUriString();
        
        return new FileUploadResponse(fileName, fileDownloadUri, file.getContentType(), file.getSize(), "SUCCESS");
    }
    
    //업로드파일(다중)
    @PostMapping("/nlipUploadMultipleFiles")
    public List<FileUploadResponse> uploadMultipleFiles(@RequestParam("files") MultipartFile[] files, @RequestParam("filepath") String filepath, @RequestParam("type") String type){
        return Arrays.asList(files)
                .stream()
                .map(file -> uploadFile(file,filepath, type))
                .collect(Collectors.toList());
    } 
    
    //다운로드파일
    @RequestMapping(value="/downloadFile", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Resource> downloadFile(@RequestParam("filepath") String filepath, HttpServletRequest request){
        Resource resource = service.loadFileAsResource(filepath);
        String contentType = null;
        
        try {
	        statusLogSaver("-------------------------------------");
	        statusLogSaver("downloadFile Call");
	        statusLogSaver("fileName : "+ filepath);
	        statusLogSaver("-------------------------------------");
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
            
        } catch (IOException ex) {
        	logSaver("-------------------------------------");
            logSaver("downloadFile Err -> Could not determine file type.");
            logSaver("fileName : "+ filepath);
            logSaver("-------------------------------------");
        }
        
        if(contentType == null) {
            contentType = "application/octet-stream";
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
        /*
		String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/downloadFile/")
                .path(fileName)
                .toUriString();
		
        return new FileUploadResponse(fileName, fileDownloadUri, contentType, file.length(), "SUCCESS");*/
    }
    
    //nlip업로드파일 및 지오서버 레이어 삭제
    @PostMapping("/nlipDeleteFile")
    public DeleteApiResponse nlipDeleteFile(@RequestBody NlipVO NlipVO) throws JsonProcessingException{
        String contentType = null;
        String id = null;
        
        try{
	        for (int i = 0; i < NlipVO.getData().size(); i++) {
	        	if(i == 0) {
	        		id = NlipVO.getData().get(i).getMsfrtnid();
	        	}
	        	statusLogSaver("-------------------------------------");
	        	statusLogSaver("JSON getLayername ->" + NlipVO.getData().get(i).getLayername());
	        	statusLogSaver("JSON getMsfrtnid ->" + NlipVO.getData().get(i).getMsfrtnid());
	        	statusLogSaver("JSON getExtention ->" + NlipVO.getData().get(i).getExtention());
	        	
	            statusLogSaver("-------------------------------------");
	        	nlipDeleteProc(NlipVO.getData().get(i).getLayername(), 
	        			NlipVO.getData().get(i).getMsfrtnid(), 
	        			NlipVO.getData().get(i).getExtention());
	        }
	        String path = updDir + id;
			deleteFilesRecursively(path, id);
			deleteFileAcc(updDir, id);
        } catch(Exception e) {
        	statusLogSaver("-------------------------------------");
        	statusLogSaver("JSON PARSE Err ->" + e);
            statusLogSaver("-------------------------------------");
        }
        if(contentType == null) {
            contentType = "application/json";
        }
        
        String deleteApiUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/nlipDeleteFile/")
                .path(id)
                .toUriString();

        return new DeleteApiResponse(id, deleteApiUri, "SUCCESS");
    }
    
    public void nlipDeleteProc(String layerNm, String msfrtnid, String type){
    	boolean result = false;
		GeoServerRESTPublisher publisher = new GeoServerRESTPublisher(geoserverUrl, geoserverAdmin, geoserverPw);
		String paramKey = msfrtnid +"_"+layerNm;

		statusLogSaver("-------------------------------------");
    	statusLogSaver("nlipDeleteProc Data");
        statusLogSaver("layerNm : "+ layerNm);
        statusLogSaver("msfrtnid : "+ msfrtnid);
        statusLogSaver("type : "+ type);
        statusLogSaver("paramKey : "+ paramKey);
        statusLogSaver("-------------------------------------");
        
    	try {
    	    //지오서버데이터삭제
    		if(type.equals("tif")) {
        	    result = publisher.removeCoverageStore("nlip", paramKey, true);
    		} else {
    			result = publisher.removeDatastore("nlip", paramKey, true);
    		}
    		//성공여부체크
    	    if(result) {
        	    statusLogSaver("Geoserver Layer Delete successful --> "+ msfrtnid + "_" + layerNm);
    	    } else {
    	    	statusLogSaver("Geoserver Layer Delete fail --> "+ msfrtnid + "_" + layerNm);
    	    }
    	} catch(Exception e) {
    		statusLogSaver("-------------------------------------");
        	statusLogSaver("nlipDeleteProc Err -> Could not delete nlip File and Geoserver");
            statusLogSaver("id : "+ msfrtnid);
            statusLogSaver("-------------------------------------");
    	}
    }
    
    public void deleteFilesRecursively(String path, String id) {
    	try {
    		//System.out.println("deletefile : " + path);
	    	File rootDir = new File(path);
    		//특정 재난 폴더 및 파일 삭제
    		if(rootDir.exists()){
                File[] folder_list = rootDir.listFiles();
                for (int i = 0; i < folder_list.length; i++) {
				    if(folder_list[i].isFile()) {
						folder_list[i].delete();
						statusLogSaver("Remove File --> "+ folder_list[i].getPath());
				    }else {
					    deleteFilesRecursively(folder_list[i].getPath(), id);
					    statusLogSaver("Remove Folder --> "+ folder_list[i].getPath());
				    }
				    folder_list[i].delete();
                }
                rootDir.delete();
    		}
    	 } catch (Exception e) {
    		e.getStackTrace();
    	}
        return;
    }
   
    public void deleteFileAcc(String path, String id) {
    	try {
	    	File rootDir = new File(path);
	    	if(rootDir.exists()) {
	    		File[] folder_list = rootDir.listFiles(); //파일리스트 얻어오기
	    		for (int i = 0; i < folder_list.length; i++) {
				    if(folder_list[i].isFile()) {
				    	if(folder_list[i].getName().contains(id)) {
				    		folder_list[i].delete();
							statusLogSaver("Remove File --> "+ folder_list[i].getPath());
				    	}
				    }
	    		}
	    	}
    	 } catch (Exception e) {
    		e.getStackTrace();
    	}
        return;
    }
    
    /**
	 * Log파일을 가져오기 위한 RequestMapping
	 * logs 폴더를 조회하고, 해당 파일을 다운로드 할 수 있는 기능.
	 * http://localhost/logFinder/logs/Error
	 */
	@RequestMapping("/logFinder/**")
	@ResponseBody
	public Object logFinder(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String path = request.getRequestURI().substring("/logFinder/".length());
		Path p = Paths.get("logs", path);
		File file = p.toFile();
		if (file.isDirectory()) {
			return file.list();
		} else {
            response.setContentType("application/octet-stream");
            response.setContentLength((int) file.length());
            StreamUtils.copy(Files.newInputStream(p, StandardOpenOption.READ), response.getOutputStream());
            return null;
        }
	}
	
	/**
	 * Error log -> file KEY append
	 * @param o
	 */
	public static void logSaver(Object o) {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss", Locale.getDefault());
		String date = sdf.format(cal.getTime());
		String out = "[" + date + "] " + o;
		
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(errFilePath, true))){
			bw.append(out);
			bw.newLine();
		} catch(Exception e) {
			System.out.println("A Fatal Error" + e);
		}
	}
	
	/**
	 * Status log -> file KEY append
	 * @param o
	 */
	public static void statusLogSaver(Object o) {
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd kk:mm:ss", Locale.getDefault());
		String date = sdf.format(cal.getTime());
		String out = "[" + date + "] " + o;
		
		try(BufferedWriter bw = new BufferedWriter(new FileWriter(stFilePath, true))){
			bw.append(out);
			bw.newLine();
		} catch(Exception e) {
			System.out.println("A Fatal Error" + e);
		}
	}
}

