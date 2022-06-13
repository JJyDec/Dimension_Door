package models

import "time"

//讲座相关数据模型
var (
	OrderTime = 	"time"
	OrderScore = 	"score"
)
type ID struct {
	id 					string 		`json:"id"`
}

// PostDetail 讲座详情
type PostDetail struct {
	PostID 				string 		`json:"post_id" db:"post_id"` 											// 帖子id
	PostPicture 		string 		`json:"post_picture,omitempty" db:"post_picture"` 						// 讲座图片(后期可以放一张图片作为默认值，放入静态资源）
	PostTitle 			string 		`json:"post_title" db:"post_title" binding:"required"` 					// 讲座标题
	NumLimit 			int 		`json:"num_limit" db:"num_limit" binding:"required"` 					// 讲座限制人数
	PostedNums 			int 		`json:"posted_nums" db:"posted_nums"` 									// 讲座报名人数
	PostTime 			string 		`json:"post_time" db:"post_time" binding:"required"` 					// 讲座开始时间
	PostPosition 		string 		`json:"post_position" db:"post_position" binding:"required"` 			// 讲座地点
	*TeacherDetail 																							// 讲师介绍
	*ParamLike																								// ParamLike 讲座点赞参数
	*PostStatus																								// PostStatus 讲座是否报名是否设置提醒
	PostIntroduction 	string 		`json:"post_introduction,omitempty" db:"post_introduction"`  			// 讲座内容简介
	CreateTime 			time.Time 	`json:"create_time" db:"create_time"`
	Score 				float32 	`json:"score,omitempty" db:"score"`										// 讲座加分
	PostType 			string 		`json:"post_type,omitempty" db:"post_type"`								// 讲座类别
}

// TeacherDetail 讲师介绍
type TeacherDetail struct {
	TeacherID 			int64 		`json:"teacher_id" db:"teacher_id"`
	TeacherName 		string 		`json:"teacher_name,omitempty" binding:"required" db:"teacher_name"` 	// 教师姓名
	TeacherSchool 		string 		`json:"teacher_school,omitempty" db:"teacher_school"` 					// 教师学校
	TeacherProfessional string 		`json:"teacher_professional,omitempty" db:"teacher_professional"` 		// 教师专业
	TeacherIntro 		string 		`json:"teacher_intro,omitempty" db:"teacher_intro"` 					// 教师简介
	// TeaVerifyID string `json:"tea_verify_id"` 															// 校验id
}

// ParamPostList 讲座列表参数
type ParamPostList struct {
	Page 				int64 		`form:"page"`
	Size 				int64 		`form:"size"`
	Order 				string 		`form:"order"`
	Type 				int64 		`form:"type"`
}

// ParamLike 讲座点赞参数
type ParamLike struct {
	LikeNums 			int64 		`json:"like_nums" db:"like_nums"` 										// 点赞数量
	LikeStatue 			float64 	`json:"like_statue"`													// 点赞状态
}

// PostDetailList 讲座详情列表
type PostDetailList struct {
	PostID 				string 		`json:"post_id" db:"post_id"` 											// 帖子id
	PostPicture 		string 		`json:"post_picture,omitempty" db:"post_picture"` 						// 讲座图片(后期可以放一张图片作为默认值，放入静态资源）
	PostTitle 			string 		`json:"post_title" db:"post_title" binding:"required"` 					// 讲座标题
	PostTime 			string 		`json:"post_time" db:"post_time" binding:"required"`					// 讲座开始时间
	PostIntroduction 	string 		`json:"post_introduction,omitempty" db:"post_introduction"`  			// 讲座内容简介
	Type				string 		`json:"type" db:"type"`													// 类型
	CreateTime 			time.Time 	`json:"create_time" db:"create_time"`									// 发布讲座时间
	*ParamLike																								// ParamLike 讲座点赞参数
	*PostStatus																								// PostStatus 讲座是否报名是否设置提醒
}

// PostStatus 讲座是否报名是否设置提醒
type PostStatus struct {
	Is_apply			bool 		`json:"is_apply"`														//讲座是否报名
	Is_remind			bool 		`json:"is_remind"`														//讲座是否设置提醒
}