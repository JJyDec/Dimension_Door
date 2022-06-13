package models

import "time"

// 报名讲座相关的接口

// ApplicationPost 报名讲座
type ApplicationPost struct {
	Openid 			string 			`json:"openid"`
	PostID 			string 			`json:"post_id"`
	CreateTime 		time.Time 		`json:"create_time"`
}
