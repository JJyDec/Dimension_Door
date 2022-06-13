package controller

import (
	"dimension_door/logic"
	"dimension_door/models"
	"github.com/gin-gonic/gin"
	"github.com/go-playground/validator/v10"
	"go.uber.org/zap"
)

// 点赞功能实现

// PostVoteHandle 点赞实现接口
func PostVoteHandle(c *gin.Context){
	//获取参数
	p := new(models.PostVote)
	if err := c.ShouldBindJSON(p);err != nil{
		errs, ok := err.(validator.ValidationErrors)
		if !ok {
			ResponseError(c,CodeInvalidParam)
			return
		}
		ResponseErrorWithMsg(c,CodeInvalidParam,removeTopStruct(errs.Translate(trans)))
		return
	}
	//获取当前用户id
	uid,err := getCurrentUser(c)
	if err != nil {
		ResponseError(c,CodeNeedLogin)
		return
	}
	//业务处理
	err = logic.PostVote(p,uid)
	if err != nil {
		zap.L().Error(" logic.PostVote(p,uid) failed err :",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}

	ResponseSuccess(c,CodeSuccess,nil)
}
