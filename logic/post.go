package logic

import (
	"dimension_door/dao/mysql"
	"dimension_door/dao/redis"
	"dimension_door/models"
	"go.uber.org/zap"
	"strconv"
)

// 处理讲座信息的函数
const (
	CanDelete = 1
)

// CreatePost 创建讲座
func CreatePost(p *models.PostDetail)error{
	//插入数据库
	err := mysql.CreatePost(p)
	if err != nil {
		zap.L().Error("mysql.CreatePost(p) failed,err:",zap.Error(err))
		return err
	}
	//插入redis
	err = redis.CreatePost(p.PostID,redis.GetMsg(p.PostType))
	if err != nil {
		zap.L().Error("err = redis.CreatePost(p)",zap.Error(err))
	}
	return err
}

// GetPostDetailByID 根据id查询讲座详情
func GetPostDetailByID(useid string,pid int64)(*models.PostDetail,error){
	postdetail ,err := mysql.GetPostDetailByID(pid)
	if err != nil {
		zap.L().Error("mysql.GetPostDetailByID(pid) failed err :",zap.Error(err))
		return nil,err
	}
	teadetail,err := mysql.GetTeacherDetailByID(postdetail.TeacherID)
	if err != nil {
		zap.L().Error("mysql.GetTeacherDetailByID(postdetail.TeacherID) failed ,err :",zap.Error(err))
		return nil,err
	}
	paramlike := &models.ParamLike{
		LikeNums:   redis.GetPostVotedByID(strconv.Itoa(int(pid))),
		LikeStatue: redis.GetPostStatues(useid,strconv.Itoa(int(pid))),
	}
	paramstatus := &models.PostStatus{
		Is_apply:  redis.GetPostApplyStatues(pid,useid),
		Is_remind: redis.GetPostRemindStatues(pid,useid),
	}
	//paramstatus  := GetPostStatuesAorR(pid,useid)
	postdetail.PostStatus = paramstatus
	postdetail.ParamLike = paramlike
	postdetail.TeacherDetail = teadetail
	return postdetail,err
}
// GetPostList 获取讲座信息
func GetPostList(p *models.ParamPostList,useid string)(data []*models.PostDetailList,err error){
	if p.Type == 0 {
		return GetPostListNotType(p,useid)
	}else{
		return GetPostListByType(p,useid)
	}
}

// GetPostListNotType 获取讲座信息没有经过筛选
func GetPostListNotType(p *models.ParamPostList,useid string)(data []*models.PostDetailList,err error){
	//从redis中按需求取id列表
	ids,err := redis.GetPostListByID(p)
	if err != nil {
		zap.L().Error("redis.GetPostListByID(p) failed err:",zap.Error(err))
		return
	}
	if len(ids) == 0{
		zap.L().Warn("redis.GetPostListByID(p) success but ids == 0")
		return
	}

	return GetPostDetailByIDS(ids,useid)
}

// GetPostDetailByIDS 根据不同的ids列表来返回 信息
func GetPostDetailByIDS(ids []string,useid string)(data []*models.PostDetailList,err error){
	postdetail,err := mysql.GetPostListByIDs(ids)
	if err != nil {
		zap.L().Error("mysql.GetPostListByIDs(ids) failed err : ",zap.Error(err))
		return
	}
	likenums,err := redis.GetPostVoted(ids)
	poststatus,err := redis.GetPostAorR(ids,useid)
	if err != nil {
		zap.L().Error("redis.GetPostVoted(ids) failed err : ",zap.Error(err))
		return
	}
	data = make([]*models.PostDetailList,0,len(postdetail))
	for idx,post := range postdetail{
		paramlike := &models.ParamLike{
			LikeNums:   likenums[idx],
			LikeStatue: redis.GetPostStatues(useid,ids[idx]),
		}
		paramstatus := &models.PostStatus{}
		if poststatus[idx] == 3 {
			paramstatus.Is_apply = 	true
			paramstatus.Is_remind = true
		}else if poststatus[idx] == 2{
			paramstatus.Is_apply = 	true
			paramstatus.Is_remind = false
		}else if poststatus[idx] == 1 {
			paramstatus.Is_apply = 	false
			paramstatus.Is_remind = true
		}else {
			paramstatus.Is_apply = 	false
			paramstatus.Is_remind = false
		}
		post.PostStatus = paramstatus
		post.ParamLike = paramlike
		data = append(data,post)
	}
	return
}

// GetPostListByType 获取讲座信息经过筛选
func GetPostListByType(p *models.ParamPostList,useid string)(data []*models.PostDetailList,err error){
	//去redis中查找id
	ids,err := redis.GetPostListIDsByType(p)
	if err != nil {
		zap.L().Error("redis.GetPostListIDsByType(p) failed err : ",zap.Error(err))
		return
	}
	if len(ids) == 0{
		zap.L().Error(" redis.GetPostListIDsByType(p) return 0 ids ")
		return
	}
	//根据id获取讲座详情列表
	postdetail,err := mysql.GetPostListByIDs(ids)
	if err != nil {
		zap.L().Error("mysql.GetPostListByIDs(ids) failed err : ",zap.Error(err))
		return
	}
	likenums,err := redis.GetPostVoted(ids)
	if err != nil {
		zap.L().Error("redis.GetPostVoted(ids) failed err : ",zap.Error(err))
		return
	}
	data = make([]*models.PostDetailList,0,len(postdetail))
	for idx,post := range postdetail{
		paramlike := &models.ParamLike{
			LikeNums:   likenums[idx],
			LikeStatue: redis.GetPostStatues(useid,ids[idx]),
		}
		post.ParamLike = paramlike
		data = append(data,post)
	}
	return
}

// RemindMyPost 提醒功能
func RemindMyPost(postid int64,userid string)bool{
	// 在redis中操作即可
	return redis.RemindMyPost(postid,userid)
}