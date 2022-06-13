package routes

import (
	"dimension_door/controller"
	_ "dimension_door/docs"
	"dimension_door/logger"
	"dimension_door/middlewares"
	"github.com/gin-gonic/gin"
)

func Setup(mode string)*gin.Engine{
	//如果设置mode为release则设置gin为该模式
	if mode == gin.ReleaseMode{
		gin.SetMode(gin.ReleaseMode)
	}

	r := gin.New()
	//r.Use(logger.GinLogger(),logger.GinRecovery(true),middlewares.RateLimitMiddleware(time.Second,1))
	r.Use(logger.GinLogger(),logger.GinRecovery(true))

	//登录接口
	r.POST("/login",controller.LoginHandle)

	//设置一个路由组
	v1 := r.Group("api/v1")
	v1.Use(middlewares.JWTAuthMiddleware())
	{
		// 用户报名接口
		v1.GET("/apply/:id",controller.ApplyHandle)
		//管理员账号发布讲座信息接口(暂时没问题 4.12)
		v1.POST("/post",controller.PostHandle)
		//管理员账号删除讲座信息接口
		v1.GET("/delete/:id",controller.DeletePostHandle)
		//查询讲座详情(暂时没问题 4.12)
		v1.GET("/post/:id",controller.GetPostDetailHandle)
		//查询讲座信息列表(由于要区别管理员界面和用户界面有没有删除按钮）
		v1.GET("/posts",controller.GetPostListHandle)
		//取消报名接口
		v1.GET("/cancel/:id",controller.CancelApplyHandle)
		// 点赞功能接口
		v1.POST("/vote",controller.PostVoteHandle)
		// 我的活动--讲座
		v1.GET("/mylecture",controller.GetMyLectureHandle)
		// 提醒功能
		v1.GET("/remind/:id",controller.RemindMyPostHandle)
		// 录入用户的姓名、学号、班级
		v1.POST("/input", controller.InputUserHandle)
	}


	return r
}