package logic

import (
	"dimension_door/dao/redis"
	"dimension_door/models"
)

// 点赞功能

// PostVote 用户对讲座进行投票
func PostVote(p *models.PostVote,uid string)error{
	return redis.PostVote(uid,p.PostID,float64(p.Direction))
}
