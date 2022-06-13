package controller

import (
	"dimension_door/logic"
	"dimension_door/models"
	"dimension_door/pkg/snowflake"
	"github.com/gin-gonic/gin"
	"github.com/go-playground/validator/v10"
	"go.uber.org/zap"
	"strconv"
	"time"
)

//讲座信息相关的函数


// PostHandle 管理员账号发送讲座信息
// @Summary 发布讲座信息
// @Description 管理员账号发布讲座的信息，其中关于id的字段不要暴露给用户操作，点赞数量，已报名人数都不可暴露
// @Tags 发布
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.PostDetail true "参数信息"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponseSuccess
// @Router /post [post]
func PostHandle(c *gin.Context){
	ok,err := CheckIsAdminAccount(c)
	if err != nil {
		zap.L().Error(" CheckIsAdminAccount(c) failed err : ",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}
	if !ok{
		ResponseError(c,CodeNeedAdmin)
		return
	}
	p := &models.PostDetail{
		PostedNums: 0,

	}
	if err := c.ShouldBindJSON(p);err != nil{
		//传入数据错误
		//是否binding错误
		errs ,ok:= err.(validator.ValidationErrors)
		if !ok {
			//参数错误
			ResponseError(c,CodeInvalidParam)
			return
		}
		//binding错误
		ResponseErrorWithMsg(c,CodeInvalidParam,removeTopStruct(errs.Translate(trans)))
		return
	}
	//雪花算法生成id
	p.PostID =  strconv.Itoa(int(snowflake.GenID()))
	// 停止一阵时间防止生成一样的id
	time.Sleep(1)
	p.TeacherID = snowflake.GenID()
	err = logic.CreatePost(p)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	// 返回响应
	ResponseSuccess(c,CodePostSuccess,nil)
}

// GetPostDetailHandle 查询某帖子详情
// @Summary 查询详情
// @Description 查询单个讲座详情
// @Tags 查询讲座信息
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.ID true "讲座id参数"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponsePost
// @Router /post/:id [get]
func GetPostDetailHandle(c *gin.Context){
	//接收参数
	idstr := c.Param("id")
	//判断参数是否正确
	pid ,err := strconv.ParseInt(idstr,10,64)
	if err != nil {
		//参数错误
		ResponseError(c,CodeInvalidParam)
		return
	}
	useid ,err := getCurrentUser(c)
	if err != nil {
		zap.L().Error("getCurrentUser(c) failed err : ",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}
	postdetial ,err := logic.GetPostDetailByID(useid,pid)
	if err != nil {
		ResponseError(c,CodeServerBusy)
		return
	}
	ResponseSuccess(c,CodeSuccess,postdetial)
}

// GetPostListHandle 获取讲座信息列表
// @Summary 查看讲座列表
// @Description 展示一页讲座列表,参数中order只有两个值可以选（time、score）目前score还未实现（4.13），size和page表示第几页展示size条记录
// @Tags 查询讲座信息
// @Accept application/json
// @Produce application/json
// @Param Authorization header string true "Bearer 用户令牌"
// @Param object query models.ParamPostList true "参数信息"
// @Security ApiKeyAuth
// @Success 200 {object} _ResponsePostList
// @Router /posts [get]
func GetPostListHandle(c *gin.Context){
	p := &models.ParamPostList{
		Page:  1,
		Size:  10,
		Order: models.OrderTime,
		Type: 0,
	}
	if err:=c.ShouldBindQuery(p);err != nil{
		//参数错误
		ResponseError(c,CodeInvalidParam)
		return
	}
	//判断是否是管理员账号
	ok,err := CheckIsAdminAccount(c)
	if err != nil {
		zap.L().Error("CheckIsAdminAccount(c) failed err :",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}

	useid,err := getCurrentUser(c)
	if err != nil {
		ResponseError(c,CodeNeedLogin)
		return
	}
	//业务处理
	data , err := logic.GetPostList(p,useid)
	if err != nil {
		zap.L().Error("logic.GetPostList(p,useid) failed err : ",zap.Error(err))
		ResponseError(c,CodeServerBusy)
		return
	}
	ResponseSuccess(c,CodeSuccess,gin.H{
		"data":data,
		"is_admin":ok,
	})
}


// RemindMyPostHandle 提醒功能实现
func RemindMyPostHandle(c *gin.Context){
	strid := c.Param("id")
	postid ,err := strconv.ParseInt(strid,10,64)
	if err != nil {
		ResponseError(c,CodeInvalidParam)
		return
	}
	userid ,err := getCurrentUser(c)
	if err != nil {
		zap.L().Error("getCurrentUser(c) failed err : ",zap.Error(err))
		ResponseError(c,CodeNeedLogin)
		return
	}
	//业务实现
	if logic.RemindMyPost(postid,userid){
		ResponseSuccess(c,CodeRemindSuccess,nil)
	}else {
		ResponseSuccess(c,CodeCancelRemindSuccess,nil)
	}

}