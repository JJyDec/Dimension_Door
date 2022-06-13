package mysql

import (
	"dimension_door/models"
	"github.com/jmoiron/sqlx"
	"go.uber.org/zap"
	"strings"
	"sync"
)

// 讲座信息相关的数据库操作

//声明互斥锁
var lock sync.RWMutex

// CreatePost 将讲座信息插入数据库
func CreatePost(p *models.PostDetail)error{
	sqlStr1 := `	insert into
				post(post_id,teacher_id,post_title,post_picture,num_limit,post_time,post_position,post_introduction,score,post_type,type)
				values(?,?,?,?,?,?,?,?,?,?,?)
`
	_,err:=db.Exec(sqlStr1,p.PostID,p.TeacherID,p.PostTitle,p.PostPicture,p.NumLimit,p.PostTime,p.PostPosition,p.PostIntroduction,p.Score,p.PostType,TypePost)

	if err != nil {
		zap.L().Error("insert into post failed err : ",zap.Error(err))
		return err
	}
	sqlStr2 := `insert into 
				teacher(teacher_id,teacher_name,teacher_school,teacher_professional,teacher_intro)
				values(?,?,?,?,?)
`
	_,err = db.Exec(sqlStr2,p.TeacherID,p.TeacherName,p.TeacherSchool,p.TeacherProfessional,p.TeacherIntro)
	return err
}

// UpdatePostedNums 更新已报名人数
func UpdatePostedNums(nums int64,postid int64,openid string)error{
	var limit int64
	//加锁 ( 需要更新的时候再加锁 4.13)
	//lock.Lock()
	//需要判定阻塞时间过长？？
	//取人数上限
	sqlStr1 := `select num_limit from post where post_id = ?`
	db.Get(&limit,sqlStr1,postid)
	if nums < limit{
		//开启事务
		tx,err := db.Begin()
		if err != nil {
			if tx != nil{
				tx.Rollback()
			}
			zap.L().Error("begin trans failed, err:",zap.Error(err))
			//lock.Unlock()
			return err
		}
		//对两个数据库进行操作
		// 经过实践发现开启事务时多线程进入更新语句，只会有一条更新成功，在没有提交事务前其余的插入更新语句将堵塞
		sqlStr := `update post set posted_nums=posted_nums + 1 where post_id = ?`
		sqlStr2 := `insert into application(openid,post_id) values(?,?)`
		ref1,err := tx.Exec(sqlStr,postid)
		if err != nil {
			tx.Rollback()
			zap.L().Error("update post set posted_nums=? where post_id failed err :",zap.Error(err))
			//lock.Unlock()
			return err
		}
		affref1,_ := ref1.RowsAffected()
		ref2,err := tx.Exec(sqlStr2,openid,postid)
		if err != nil {
			tx.Rollback()
			zap.L().Error("insert into application(openid,postid) values(?,?) failed err :",zap.Error(err))
			//lock.Unlock()
			return err
		}
		affref2 ,_ := ref2.RowsAffected()
		if affref2 == 1 && affref1 == 1{
			//提交事务
			//防止并发执行时，超出了报名人数仍然报名成功，此处新增一个判断是否会超出报名人数。
			sqlstr3 := `select posted_nums from post where post_id = ?`
			var num int64
			db.Get(&num,sqlstr3)
			if num > limit{
				tx.Rollback()
				//lock.Unlock()
				return ErrorPostLimit
			}
			tx.Commit()
			//lock.Unlock()
			return nil
		}else{
			tx.Rollback()
			//lock.Unlock()
			return ErrorAffectNotOneMsg
		}

	}
	err := ErrorPostLimit
	//lock.Unlock()
	return  err
}

// GetPostDetailByID 根据讲座id查看讲座详情
func GetPostDetailByID(pid int64)(postdetail *models.PostDetail,err error){
	postdetail = new(models.PostDetail)
	lock.RLock()
	sqlStr := `select post_id,teacher_id,post_picture,post_title,post_position,num_limit,posted_nums,score,post_time,create_time,post_type,post_introduction
				from post
				where post_id = ?
`
	err = db.Get(postdetail,sqlStr,pid)
	lock.RUnlock()
	return
}

// GetTeacherDetailByID 根据教师ID查看教师个人信息
func GetTeacherDetailByID(tid int64)(teadetail *models.TeacherDetail,err error){
	teadetail = new(models.TeacherDetail)
	sqlStr := `select teacher_id,teacher_name,teacher_school,teacher_professional,teacher_intro
				from teacher
				where teacher_id = ?
`
	err =  db.Get(teadetail,sqlStr,tid)
	return
}

// GetPostListByIDs 根据id列表查询所有讲座信息列表
//func GetPostListByIDs(ids []string)(data []*models.PostDetail,err error){
//	sqlStr := `	select post_id,teacher_id,post_picture,post_title,post_position,num_limit,posted_nums,post_time,create_time
//				from post
//				where post_id in (?)
//				order by FIND_IN_SET(post_id,?)
//`
//	query,args,err :=sqlx.In(sqlStr,ids,strings.Join(ids,","))
//	if err != nil {
//		return
//	}
//	query = db.Rebind(query)
//	err = db.Select(&data,query,args...)
//	return
//}

// GetPostListByIDs 根据id列表查询讲座信息列表
func GetPostListByIDs(ids []string)(data []*models.PostDetailList,err error){
	sqlStr := `	select post_id,post_picture,post_title,post_time,create_time,post_introduction,type 
				from post
				where post_id in (?)
				order by FIND_IN_SET(post_id,?)
`
	query,args,err :=sqlx.In(sqlStr,ids,strings.Join(ids,","))
	if err != nil {
		return
	}
	query = db.Rebind(query)
	err = db.Select(&data,query,args...)
	return
}