package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {

		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {

		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());

		//Task.26 鈴井文太
		//時間マップと分マップの設定
		attendanceForm.setTrainingHours(attendanceUtil.setTrainingHourMap());
		attendanceForm.setTrainingMinutes(attendanceUtil.setTrainingMinuteMap());
		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}

		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			//Task.26 鈴木文太
			//出勤時間から時間と分を取得
			if (dailyAttendanceForm.getTrainingStartTime() != null && dailyAttendanceForm.getTrainingStartTime() != ""
					&& dailyAttendanceForm.getTrainingStartTime().length() != 0) {
				dailyAttendanceForm.setTrainingStartHour(attendanceUtil.getHour(
						attendanceManagementDto.getTrainingStartTime()));
				dailyAttendanceForm.setTrainingStartMinute(attendanceUtil.getMinute(
						attendanceManagementDto.getTrainingStartTime()));
				//画面表示用の数値を設定
				dailyAttendanceForm.setTrainingStartDispHour(attendanceUtil.getTrainingHourValue(
						dailyAttendanceForm.getTrainingStartHour()));
				dailyAttendanceForm.setTrainingStartDispMinute(attendanceUtil.getTrainingMinuteValue(
						dailyAttendanceForm.getTrainingStartMinute()));
			} else {
				dailyAttendanceForm.setTrainingStartHour(null);
				dailyAttendanceForm.setTrainingStartMinute(null);
				dailyAttendanceForm.setTrainingStartDispHour("");
				dailyAttendanceForm.setTrainingStartDispMinute("");
			}

			//退勤時間の分割
			if (dailyAttendanceForm.getTrainingEndTime() != null && dailyAttendanceForm.getTrainingEndTime() != "" &&
					dailyAttendanceForm.getTrainingEndTime().length() != 0) {
				dailyAttendanceForm.setTrainingEndHour(attendanceUtil.getHour(
						attendanceManagementDto.getTrainingEndTime()));
				dailyAttendanceForm.setTrainingEndMinute(attendanceUtil.getMinute(
						attendanceManagementDto.getTrainingEndTime()));
				//画面表示用の数値の設定				
				dailyAttendanceForm.setTrainingEndDispHour(attendanceUtil.getTrainingHourValue(
						dailyAttendanceForm.getTrainingEndHour()));
				dailyAttendanceForm.setTrainingEndDispMinute(attendanceUtil.getTrainingHourValue(
						dailyAttendanceForm.getTrainingEndMinute()));
			} else {
				dailyAttendanceForm.setTrainingEndHour(null);
				dailyAttendanceForm.setTrainingEndMinute(null);
				dailyAttendanceForm.setTrainingEndDispHour("");
				dailyAttendanceForm.setTrainingEndDispMinute("");
			}
			//Task.26ここまで

			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());

			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}

		return attendanceForm;
	}
	
	//Task.27 鈴木文太
	/**
	 * エラー発生時 時間マップ 分マップ 中抜け時間を取得する処理
	 * @param
	 * @return
	 */
	public AttendanceForm setAttendanceFormValid(
			AttendanceForm attendanceForm,List<AttendanceManagementDto> attendanceManagementDtoList) {
		
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		attendanceForm.setTrainingHours(attendanceUtil.setTrainingHourMap());
		attendanceForm.setTrainingMinutes(attendanceUtil.setTrainingMinuteMap());
		
		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
				for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
					DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
					dailyAttendanceForm
							.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
					dailyAttendanceForm
							.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
					dailyAttendanceForm
							.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
					dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
					//Task.26 鈴木文太
					//出勤時間から時間と分を取得
					if (dailyAttendanceForm.getTrainingStartTime() != null && dailyAttendanceForm.getTrainingStartTime() != ""
							&& dailyAttendanceForm.getTrainingStartTime().length() != 0) {
						dailyAttendanceForm.setTrainingStartHour(attendanceUtil.getHour(
								attendanceManagementDto.getTrainingStartTime()));
						dailyAttendanceForm.setTrainingStartMinute(attendanceUtil.getMinute(
								attendanceManagementDto.getTrainingStartTime()));
						//画面表示用の数値を設定
						dailyAttendanceForm.setTrainingStartDispHour(attendanceUtil.getTrainingHourValue(
								dailyAttendanceForm.getTrainingStartHour()));
						dailyAttendanceForm.setTrainingStartDispMinute(attendanceUtil.getTrainingMinuteValue(
								dailyAttendanceForm.getTrainingStartMinute()));
					} else {
						dailyAttendanceForm.setTrainingStartHour(null);
						dailyAttendanceForm.setTrainingStartMinute(null);
						dailyAttendanceForm.setTrainingStartDispHour("");
						dailyAttendanceForm.setTrainingStartDispMinute("");
					}

					//退勤時間の分割
					if (dailyAttendanceForm.getTrainingEndTime() != null && dailyAttendanceForm.getTrainingEndTime() != "" &&
							dailyAttendanceForm.getTrainingEndTime().length() != 0) {
						dailyAttendanceForm.setTrainingEndHour(attendanceUtil.getHour(
								attendanceManagementDto.getTrainingEndTime()));
						dailyAttendanceForm.setTrainingEndMinute(attendanceUtil.getMinute(
								attendanceManagementDto.getTrainingEndTime()));
						//画面表示用の数値の設定				
						dailyAttendanceForm.setTrainingEndDispHour(attendanceUtil.getTrainingHourValue(
								dailyAttendanceForm.getTrainingEndHour()));
						dailyAttendanceForm.setTrainingEndDispMinute(attendanceUtil.getTrainingHourValue(
								dailyAttendanceForm.getTrainingEndMinute()));
					} else {
						dailyAttendanceForm.setTrainingEndHour(null);
						dailyAttendanceForm.setTrainingEndMinute(null);
						dailyAttendanceForm.setTrainingEndDispHour("");
						dailyAttendanceForm.setTrainingEndDispMinute("");
					}
					//Task.26ここまで

					if (attendanceManagementDto.getBlankTime() != null) {
						dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
						dailyAttendanceForm.setBlankTimeValue(String.valueOf(
								attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
					}
				}
		
		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// Task.26 鈴木文太
			//			時間と分を結合して出力
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			if (dailyAttendanceForm.getTrainingStartHour() != null
					&& dailyAttendanceForm.getTrainingStartMinute() != null) {
				dailyAttendanceForm.setTrainingStartTime(dailyAttendanceForm.getTrainingStartHour() + ":"
						+ dailyAttendanceForm.getTrainingStartMinute());
				trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
				tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingStartTime("");
			}

			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			if (dailyAttendanceForm.getTrainingEndHour() != null
					&& dailyAttendanceForm.getTrainingEndMinute() != null) {
				dailyAttendanceForm.setTrainingEndTime(dailyAttendanceForm.getTrainingEndHour() + ":"
						+ dailyAttendanceForm.getTrainingEndMinute());
				trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
				tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			} else {
				tStudentAttendance.setTrainingEndTime("");
			}

			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	//Task.25 鈴木文太
	/**
	 * 勤怠情報の未入力の件数を取得
	 * @param lmsUserId
	 * @return 未入力の件数
	 */
	public Integer getNotEnterCount(Integer lmsUserId) {
		try {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date trainingDate = sdf.parse(sdf.format(new Date()));
			Integer notEnterCount = tStudentAttendanceMapper.notEnterCount(lmsUserId, Constants.DB_FLG_FALSE,
					trainingDate);
			return notEnterCount;
		} catch (ParseException e) {
			e.printStackTrace();
			return 0;
		}
	}

	//	Task.27 鈴木文太
	@Autowired
	private MessageSource messageSource;
	/**
	 * 更新押下時の入力チェック
	 * @param attendanceForm
	 * @param bindingResult
	 * @return エラーメッセージ
	 * @throws ParseException
	 */
	public BindingResult updateCheck(AttendanceForm attendanceForm, BindingResult bindingResult) throws ParseException {
		//エラーメッセージを配列を使って格納
		Set<String> errorList = new HashSet<>();
		Integer startHour = null;
		Integer startMinute = null;
		Integer endHour = null;
		Integer endMinute = null;
		//本日の日付
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
		Date trainingDate = sdf.parse(sdf.format(new Date()));
		
		Integer index = 0;

		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {
			
			//フォーム内の日付
			Date date = sdf.parse(dailyAttendanceForm.getTrainingDate());
			
			startHour = null;
			startMinute = null;
			endHour = null;
			endMinute = null;
			
			//日付が当日よりも前の場合
			if ((date.compareTo(trainingDate)) == -1) {
				if (dailyAttendanceForm.getTrainingStartHour() != null) {
					startHour = dailyAttendanceForm.getTrainingStartHour();
				}
				if (dailyAttendanceForm.getTrainingStartMinute() != null) {
					startMinute = dailyAttendanceForm.getTrainingStartMinute();
				}
				if (dailyAttendanceForm.getTrainingEndHour() != null) {
					endHour = dailyAttendanceForm.getTrainingEndHour();
				}
				if (dailyAttendanceForm.getTrainingEndMinute() != null) {
					endMinute = dailyAttendanceForm.getTrainingEndMinute();
				}
				//備考欄の文字数制限
				if (dailyAttendanceForm.getNote().length() >= 100) {
					String[] str = { messageSource.getMessage("placeNote", new String[] {}, Locale.getDefault()),
							"100" };
					String error = messageUtil.getMessage(Constants.VALID_KEY_MAXLENGTH, str);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "note", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//時と分の一方のみ記入の場合
				//出勤時間（時）
				if (startHour == null && startMinute != null) {
					String[] str = { "出勤時間" };
					String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "attendanceList[" + index + "].trainingStartHour", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//出勤時間（分）
				if (startHour != null && startMinute == null) {
					String[] str = { "出勤時間" };
					String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "attendanceList[" + index + "].trainingStartMinute", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//退勤時間（時）
				if (endHour == null && endMinute != null) {
					String[] str = { "退勤時間" };
					String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "attendanceList[" + index + "].trainingEndHour", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//退勤時間（時）
				if (endHour != null && endMinute == null) {
					String[] str = { "退勤時間" };
					String error = messageUtil.getMessage(Constants.INPUT_INVALID, str);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "attendanceList[" + index + "].trainingEndMinute", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//退勤時間のみ記入の場合
				if (startHour == null && startMinute == null && endHour != null && endMinute != null) {
					String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "EndOnly", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				//退勤時間より出勤時間の方が多い場合
				if ((startHour != null && startMinute != null && endHour != null && endMinute != null)) {
					Integer startTime = startHour*100+startMinute;
					Integer endTime = endHour*100+endMinute;
					if (startTime-endTime >= 0) {
						Integer listN = attendanceForm.getAttendanceList().size();
						String[] list = { String.valueOf(listN) };
						String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE, list);
						FieldError fieldError = new FieldError(bindingResult.getObjectName(), "trainingTimeOver",
								error);
						bindingResult.addError(fieldError);
						errorList.add(error);
					}
				}
				//中抜き時間が勤務時間を上回っている場合
				int hour;
				int minute;
				int trainingMinute = 0;
				if (startHour != null && startMinute != null &&
						endHour != null && endMinute != null) {
					hour = (endHour - startHour) * 60;
					minute = endMinute - startMinute;
					trainingMinute = hour + minute;
				}
				if (dailyAttendanceForm.getBlankTime() != null && trainingMinute < dailyAttendanceForm.getBlankTime()) {
					String error = messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_BLANKTIMEERROR);
					FieldError fieldError = new FieldError(bindingResult.getObjectName(), "blankTime", error);
					bindingResult.addError(fieldError);
					errorList.add(error);
				}
				System.out.println(dailyAttendanceForm.getTrainingDate());
			}
			index++;
		}
		attendanceForm.setErrorList(errorList);
		return bindingResult;
	}

}
