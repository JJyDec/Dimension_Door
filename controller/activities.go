package controller

import (
	"dimension_door/logic"
	"dimension_door/models"
	"github.com/gin-gonic/gin"
)

// “我的活动”功能实现


// GetMyLectureHandle 获取“我的讲座”
func GetMyLectureHandle(c *gin.Context){
	p := &models.ParamPostList{
		Page:  1,
		Size:  10,
	}
	if err:=c.ShouldBindQuery(p);err != nil{
		//参数错误
		ResponseError(c,CodeInvalidParam)
		return
	}
	//获取useid
	userid,err := getCurrentUser(c)
	if err != nil {
		ResponseError(c,CodeNeedLogin)
		return
	}
	//业务处理
	data ,err := logic.GetMyLecture(p,userid)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	ResponseSuccess(c,CodeSuccess,data)
}
