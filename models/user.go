package models

// WXLoginResp 接收微信服务器返回值的数据模型
type WXLoginResp struct {
	Openid 			string 			`json:"openid"`
	SessionKey 		string 			`json:"session_key"`
	Unionid 		string 			`json:"unionid"`
	Errcode 		int 			`json:"errcode"`
	Errmsg 			string 			`json:"errmsg"`
}

// Usermsg 接收前端用户个人信息的数据模型
type Usermsg struct {
	Code 			string 			`json:"code"  bind:"required"` 		//微信临时令牌
	Username 		string 			`json:"username" db:"username"` 	// 用户名
	Picture 		string 			`json:"picture"` 					// 用户头像
	Gender 			int 			`json:"gender,omitempty"` 			// 用户性别（默认0）
}

// StudentMsg 学生信息
type StudentMsg struct {
	StudentId int64  `json:"student_id" bind:"required"` // 学生学号
	Name      string `json:"name" bind:"required" `      // 学生姓名
	Class     string `json:"class" bind:"required"`      // 班级
}