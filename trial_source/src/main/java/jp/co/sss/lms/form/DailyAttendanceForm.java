package jp.co.sss.lms.form;

import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	/** 退勤時間 */
	private String trainingEndTime;
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;
	
	//Task.26 鈴木文太
	/** 出勤時間(時間) */
	private String trainingStartHour;
	/** 出勤時間(分) */
	private String trainingStartMinute;
	/** 出勤時間　時間(画面表示用) */
	private Integer trainingStartDispHour;
	/** 出勤時間　分(画面表示用) */
	private Integer trainingStartDispMinute;
	/** 退勤時間(時間) */
	/** 退勤時間(分) */
	/** 退勤時間　時間(画面表示用) */
	/** 退勤時間　分(画面表示用) */

}
