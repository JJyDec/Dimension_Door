package redis

import (
	"dimension_door/models"
	"github.com/go-redis/redis"
	"strconv"
	"time"
)

// 和发布讲座相关的redis处理

// CreatePost 创建帖子并根据时间戳给其赋分
func CreatePost(pid string ,posttype int64)error{
	//开启事务
	pipeline := rdb.TxPipeline()
	//插入时间戳（后期要按时间排序）
	pipeline.ZAdd(getRedisKey(KeyPostTimeZset),redis.Z{
		Score:  float64(time.Now().Unix()),
		Member: pid,
	})
	//插入分数(后期按点赞分数排序）
	pipeline.ZAdd(getRedisKey(KeyPostScoreZset),redis.Z{
		Score:  10000,
		Member: pid,
	})
	//加到type中
	tkey := getRedisKey(KeyPostTypeSetPF + strconv.Itoa(int(posttype)))
	pipeline.SAdd(tkey,pid)
	_,err := pipeline.Exec()
	return err
}

// GetPostListByID 从redis中根据用户请求返回id列表
func GetPostListByID(p *models.ParamPostList)([] string,error){
	//从redis中取用户id
	userNeedStr := getRedisKey(KeyPostTimeZset)
	if p.Order == models.OrderScore{
		userNeedStr = getRedisKey(KeyPostScoreZset)
	}
	return getIDsFromKey(userNeedStr,p.Page,p.Size)
}

// getIDsFromKey 在redis查一个范围的数据
func getIDsFromKey(key string,page int64,size int64)([] string,error){
	start :=(page - 1)*size
	end :=start + size - 1
	return rdb.ZRevRange(key,start,end).Result()
}

// GetPostListIDsByType 在redis中根据用户请求返回id列表（根据type）
func GetPostListIDsByType(p *models.ParamPostList)([] string,error){
	userNeedStr := getRedisKey(KeyPostTimeZset)
	if p.Order == models.OrderScore{
		userNeedStr = getRedisKey(KeyPostScoreZset)
	}
	//拼凑出最终的key 查看是否有
	key := userNeedStr + strconv.Itoa(int(p.Type))
	if rdb.Exists(key).Val() < 1 {
		//不存在
		tkey := getRedisKey(KeyPostTypeSetPF + strconv.Itoa(int(p.Type)))
		pipeline := rdb.Pipeline()
		pipeline.ZInterStore(key,redis.ZStore{
			Aggregate: "MAX",
		},tkey,userNeedStr)
		pipeline.Expire(key,60*time.Second)
		_,err := pipeline.Exec()
		if err != nil {
			return nil,err
		}
	}
	return getIDsFromKey(key,p.Page,p.Size)
}

// GetMyLectureIDs 获取“我的讲座” id列表
func GetMyLectureIDs(p *models.ParamPostList,userid string)( []string, error){
	key := getRedisKey(KeyMyLectureSetPF) + userid
	//sortby := getRedisKey(KeyPostTimeZset)
	if rdb.Exists(key).Val() < 1 {
		//不存在取交集
		remindkey := getRedisKey(KeyLectureRemindZsetPF + userid)
		applykey := getRedisKey(KeyLectureApplicationZsetPF + userid)
		pipeline := rdb.Pipeline()
		pipeline.ZUnionStore(key,redis.ZStore{
			Aggregate: "MAX",
		},remindkey,applykey)
		pipeline.Expire(key,time.Second)
		_,err := pipeline.Exec()
		if err != nil {
			return nil,err
		}
	}
	return getIDsFromKey(key,p.Page,p.Size)
}


// ApplyInsertRedis 向redis中插入报名信息
func ApplyInsertRedis(postid int64,userid string){
	applykey := getRedisKey(KeyLectureApplicationZsetPF + userid)
	remindkey := getRedisKey(KeyLectureRemindZsetPF+userid)
	rdb.SAdd(applykey,postid)
	rdb.SAdd(remindkey,postid)
}

// GetPostAorR 获取是否报名或提醒（1表示设置提醒，2表示设置报名）
func GetPostAorR(ids []string,userid string)(data []int64,err error){
	pipeline := rdb.TxPipeline()
	for _,id := range ids{
		pipeline.SIsMember(getRedisKey(KeyLectureRemindZsetPF + userid),id)
	}
	cmders,err := pipeline.Exec()
	if err != nil {
		return nil,err
	}
	data = make([]int64,0,len(cmders))
	for _,cmder := range cmders{
		v := cmder.(*redis.BoolCmd).Val()
		var x int64
		if v {
			x = 1
		}else {
			x = 0
		}
		data = append(data,x)
	}

	for _,id := range ids{
		pipeline.SIsMember(getRedisKey(KeyLectureApplicationZsetPF + userid),id)
	}
	cmder2s,err := pipeline.Exec()
	if err != nil {
		return nil,err
	}
	for idx,cmder := range cmder2s{
		v := cmder.(*redis.BoolCmd).Val()
		var x int64
		if v {
			x = 2
		}else {
			x = 0
		}
		data[idx] += x
	}
	return
}


// GetPostStatuesAorR 获取讲座提醒和报名状态
//func GetPostStatuesAorR(postid int64,userid string)(param *models.PostStatus){
//	param.Is_remind = rdb.SIsMember(getRedisKey(KeyLectureRemindZsetPF + userid),postid).Val()
//	param.Is_apply = rdb.SIsMember(getRedisKey(KeyLectureApplicationZsetPF + userid),postid).Val()
//	return
//}

// GetPostApplyStatues 获取讲座是否报名
func GetPostApplyStatues(postid int64,userid string)bool{
	return rdb.SIsMember(getRedisKey(KeyLectureApplicationZsetPF+userid),postid).Val()
}

// GetPostRemindStatues 获取讲座是否设置提醒
func GetPostRemindStatues(postid int64,userid string)bool{
	return rdb.SIsMember(getRedisKey(KeyLectureRemindZsetPF+userid),postid).Val()
}

// RemindMyPost 提醒功能
func RemindMyPost(postid int64,userid string)bool{
	//是否存在
	key := getRedisKey(KeyLectureRemindZsetPF+userid)
	exist := rdb.SIsMember(key,postid).Val()
	if exist {
		//存在则删除(存在即返回插入失败）
		rdb.SRem(key,postid)
		return false
	}else {
		//不存在则插入(不存在即返回插入成功）
		rdb.SAdd(key,postid)
		return true
	}
}