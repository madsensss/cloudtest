package file.service;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

import file.entity.AttachmentFile;
import file.repository.AttachmentFileRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Service
public class S3Service {

	private final AmazonS3 amazonS3;
	private final AttachmentFileRepository fileRepository;

	@Value("${cloud.aws.s3.bucket}")
	private String bucketName;

	private final String DIR_NAME = "s3_data";

	// 파일 업로드
	@Transactional
	public void uploadS3File(MultipartFile file) throws Exception {
		if (file == null) {
			throw new Exception("파일 전달 오류 발생");
		}
		String filePath = "C://CE//97.data//" + DIR_NAME;
		String attachmentOriginalFileName = file.getOriginalFilename();
		UUID uuid = UUID.randomUUID();
		String attachmentFilename = uuid.toString() + "_" + attachmentOriginalFileName;
		Long attachmentFileSize = file.getSize();
		AttachmentFile attachmentFile = AttachmentFile.builder().attachmentFileName(attachmentFilename)
				.attachmentFileSize(attachmentFileSize).attachmentOriginalFileName(attachmentOriginalFileName)
				.filePath(filePath).build();
		Long fileNo = fileRepository.save(attachmentFile).getAttachmentFileNo();
		if (fileNo != null) {
			System.out.println(attachmentFile.getFilePath());
			File uploadFile = new File(attachmentFile.getFilePath() + "//" + attachmentFilename);
			file.transferTo(uploadFile);
			String S3Key = DIR_NAME + "/" + uploadFile.getName();
			amazonS3.putObject(bucketName, S3Key, uploadFile);
			if (uploadFile.exists()) {
				uploadFile.delete();
			}
		}
	}

	// 파일 다운로드
	@Transactional
	public ResponseEntity<Resource> downloadS3File(long fileNo) {
		
		AttachmentFile attachmentFile = fileRepository.findById(fileNo)
				.orElseThrow(() -> new NoSuchElementException("파일없음"));
		Resource resource = null;
		
		try {
			
			S3Object s3object = amazonS3.getObject(bucketName, DIR_NAME + '/' + attachmentFile.getAttachmentFileName());
			S3ObjectInputStream is = s3object.getObjectContent();
			resource = new InputStreamResource(is);
			
		} catch (Exception e) {
			
			return new ResponseEntity<Resource>(resource, null, HttpStatus.NO_CONTENT);
			
		}
		
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
		headers.setContentDisposition(ContentDisposition.builder("attachment")
				.filename(attachmentFile.getAttachmentOriginalFileName()).build());

		return new ResponseEntity<Resource>(resource, headers, HttpStatus.OK);
	}

}