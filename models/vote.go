package models


// 投票有关的数据模型

// PostVote 讲座状态和帖子id
type PostVote struct {
	PostID 		string 		`json:"post_id" binding:"required"`
	Direction	int8 		`json:"direction,string" binding:"oneof=1 0"`
}