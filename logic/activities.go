package logic

import (
	"dimension_door/dao/redis"
	"dimension_door/models"
	"go.uber.org/zap"
)

// “我的活动”功能实现


// GetMyLecture 获取我的讲座信息列表
func GetMyLecture(p *models.ParamPostList,userid string)(data []*models.PostDetailList,err error){
	ids,err := redis.GetMyLectureIDs(p,userid)
	if err != nil {
		zap.L().Error("redis.GetPostListIDsByType(p) failed err : ",zap.Error(err))
		return
	}
	if len(ids) == 0{
		zap.L().Error(" redis.GetPostListIDsByType(p) return 0 ids ")
		return
	}

	return GetPostDetailByIDS(ids,userid)
}
