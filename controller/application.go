package controller

import (
	"dimension_door/dao/mysql"
	"dimension_door/dao/redis"
	"dimension_door/logic"
	"errors"
	"github.com/gin-gonic/gin"
	"go.uber.org/zap"
	"strconv"
)

// 和报名相关接口

// ApplyHandle 报名讲座
// @Summary 用户报名讲座接口
// @Description 用户完成对具体讲座的报名并返回结果
// @Tags 报名
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.ID true "讲座id"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponseSuccess
// @Router /apply/:id [get]
func ApplyHandle(c *gin.Context){
	// 接收帖子id
	strid := c.Param("id")

	//校验参数
	postid ,err := strconv.ParseInt(strid,10,64)
	if err != nil {
		//错误的帖子id
		ResponseError(c,CodeInvalidParam)
		return
	}
	// 确定报名者（获取报名者）
	openid ,err := getCurrentUser(c)
	if err != nil {
		//用户未登录
		ResponseError(c,CodeNeedLogin)
		return
	}
	if err = logic.Application(postid,openid);err != nil{
		//重复报名
		if errors.Is(err,mysql.ErrorApplyAgain){
			ResponseError(c,CodeNotApplyAgain)
			return
		}
		//达到人数上限
		if errors.Is(err, mysql.ErrorPostLimit){
			ResponseError(c,CodePostLimit)
			return
		}
		//代码原因出错了（插入数据库失败）
		zap.L().Error("logic.Application(postid,openid) failed:",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}

	//返回响应
	ResponseSuccess(c,CodeApplySuccess,nil)
}

// CancelApplyHandle 取消报名api
// @Summary 用户取消报名
// @Description 用户取消报名
// @Tags 取消报名
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.ID true "参数信息"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponseSuccess
// @Router /cancel/:id [get]
func CancelApplyHandle(c *gin.Context){
	strid := c.Param("id")
	pid ,err := strconv.ParseInt(strid,10,64)
	if err != nil {
		ResponseError(c,CodeInvalidParam)
		return
	}
	openid ,err := getCurrentUser(c)
	if err != nil {
		zap.L().Error("getCurrentUser(c) failed err : ",zap.Error(err))
		ResponseError(c,CodeNeedLogin)
		return
	}
	err = logic.CancelApplyByID(pid,openid)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	redis.RemApplyKey(pid,openid)
	ResponseSuccess(c,CodeCancelSuccess,nil)
}